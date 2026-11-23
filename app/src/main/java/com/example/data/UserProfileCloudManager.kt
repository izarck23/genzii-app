package com.example.data

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.auth.awaitTask
import com.example.model.SubscriptionTier
import com.example.model.ThemeMode
import com.example.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object UserProfileCloudManager {
  private const val TAG = "UserProfileCloudManager"
  private const val COLLECTION_USERS = "users"
  private const val PROFILE_IMAGE_FILENAME = "profile_avatar.jpg"

  private fun getFirestore(context: Context): FirebaseFirestore {
    return try {
      val resId = context.resources.getIdentifier("firestore_database_id", "string", context.packageName)
      if (resId != 0) {
        val dbId = context.getString(resId)
        if (dbId.isNotBlank() && dbId != "(default)") {
          FirebaseFirestore.getInstance(dbId)
        } else {
          FirebaseFirestore.getInstance()
        }
      } else {
        FirebaseFirestore.getInstance()
      }
    } catch (_: Exception) {
      FirebaseFirestore.getInstance()
    }
  }

  private fun getStorage(): FirebaseStorage {
    return FirebaseStorage.getInstance()
  }

  /**
   * Fetches the user profile from Cloud Firestore and updates local repository state.
   */
  suspend fun fetchAndSyncUserProfile(context: Context, uid: String): Result<UserProfile> = withContext(Dispatchers.IO) {
    try {
      val db = getFirestore(context)
      val snapshot = db.collection(COLLECTION_USERS).document(uid).get().awaitTask()
      if (snapshot.exists()) {
        val data = snapshot.data ?: emptyMap<String, Any?>()
        val displayName = data["displayName"] as? String ?: data["name"] as? String ?: ""
        val email = data["email"] as? String ?: ""
        val role = data["role"] as? String ?: "Student / Researcher"
        val institution = data["institution"] as? String ?: "Academic Institute"
        val photoUrl = data["photoUrl"] as? String
        val tierStr = data["tier"] as? String ?: "FREE"
        val tier = try { SubscriptionTier.valueOf(tierStr) } catch (_: Exception) { SubscriptionTier.FREE }

        // Also check if local permanent avatar exists
        val localAvatarFile = File(context.filesDir, PROFILE_IMAGE_FILENAME)
        val finalPhotoUrl = if (localAvatarFile.exists() && localAvatarFile.length() > 0) {
          Uri.fromFile(localAvatarFile).toString()
        } else {
          photoUrl
        }

        GenziiRepository.updateProfile(
          name = displayName,
          email = email,
          role = role,
          institution = institution
        )
        if (!finalPhotoUrl.isNullOrBlank()) {
          GenziiRepository.updateProfilePhoto(finalPhotoUrl)
        }
        GenziiRepository.upgradeTier(tier)

        Log.d(TAG, "Successfully synced user profile from Firestore: $displayName ($email)")
        Result.success(GenziiRepository.userProfile.value)
      } else {
        // Document does not exist yet; bootstrap it from Firebase Auth
        val authUser = FirebaseAuth.getInstance().currentUser
        if (authUser != null) {
          saveUserProfileToFirestore(
            context = context,
            uid = uid,
            displayName = authUser.displayName ?: authUser.email?.substringBefore("@") ?: "Scholar",
            email = authUser.email ?: "",
            photoUrl = authUser.photoUrl?.toString()
          )
        }
        Result.success(GenziiRepository.userProfile.value)
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to fetch user profile from Firestore: ${e.message}")
      Result.failure(e)
    }
  }

  /**
   * Saves or updates the user profile record in Cloud Firestore under users/{uid}.
   */
  suspend fun saveUserProfileToFirestore(
    context: Context,
    uid: String,
    displayName: String,
    email: String,
    photoUrl: String? = null,
    role: String = "Student / Researcher",
    institution: String = "Academic Institute"
  ): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val db = getFirestore(context)
      val userRef = db.collection(COLLECTION_USERS).document(uid)

      val profileData = hashMapOf<String, Any?>(
        "uid" to uid,
        "displayName" to displayName,
        "name" to displayName,
        "email" to email,
        "role" to role,
        "institution" to institution,
        "updatedAt" to System.currentTimeMillis()
      )
      if (photoUrl != null) {
        profileData["photoUrl"] = photoUrl
      }

      userRef.set(profileData, SetOptions.merge()).awaitTask()
      Log.d(TAG, "Successfully saved profile to Firestore for user: $uid")
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save profile to Firestore: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Saves avatar image permanently to local storage and uploads to Firebase Storage (users/{uid}/profile/profile_image).
   */
  suspend fun uploadProfileImage(
    context: Context,
    imageUri: Uri,
    uid: String
  ): Result<String> = withContext(Dispatchers.IO) {
    try {
      // 1. Save locally to private app files directory so it never disappears across restarts
      val localAvatar = File(context.filesDir, PROFILE_IMAGE_FILENAME)
      context.contentResolver.openInputStream(imageUri)?.use { input ->
        FileOutputStream(localAvatar).use { output ->
          input.copyTo(output)
        }
      }
      val permanentLocalUri = Uri.fromFile(localAvatar).toString()
      GenziiRepository.updateProfilePhoto(permanentLocalUri)

      // 2. Upload to Firebase Storage: users/{uid}/profile/profile_image
      var remoteUrl: String? = null
      try {
        val storageRef = getStorage().reference.child("users/$uid/profile/profile_image.jpg")
        storageRef.putFile(Uri.fromFile(localAvatar)).awaitTask()
        val downloadUri = storageRef.downloadUrl.awaitTask()
        remoteUrl = downloadUri.toString()
        Log.d(TAG, "Uploaded avatar to Firebase Storage: $remoteUrl")

        // Update Firebase Auth profile photo
        val authUser = FirebaseAuth.getInstance().currentUser
        if (authUser != null) {
          val changeRequest = UserProfileChangeRequest.Builder()
            .setPhotoUri(downloadUri)
            .build()
          authUser.updateProfile(changeRequest).awaitTask()
        }
      } catch (storageEx: Exception) {
        Log.w(TAG, "Firebase Storage upload fallback (offline / rules restriction): ${storageEx.message}")
      }

      // 3. Update Firestore profile record
      val db = getFirestore(context)
      val updates = hashMapOf<String, Any>(
        "photoUrl" to (remoteUrl ?: permanentLocalUri),
        "updatedAt" to System.currentTimeMillis()
      )
      try {
        db.collection(COLLECTION_USERS).document(uid).set(updates, SetOptions.merge()).awaitTask()
      } catch (firestoreEx: Exception) {
        Log.w(TAG, "Firestore avatar metadata update fallback: ${firestoreEx.message}")
      }

      Result.success(remoteUrl ?: permanentLocalUri)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to process profile image: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Removes profile photo from local storage and updates Firestore.
   */
  suspend fun removeProfileImage(context: Context, uid: String): Result<Unit> = withContext(Dispatchers.IO) {
    try {
      val localAvatar = File(context.filesDir, PROFILE_IMAGE_FILENAME)
      if (localAvatar.exists()) {
        localAvatar.delete()
      }
      GenziiRepository.updateProfilePhoto(null)

      try {
        val storageRef = getStorage().reference.child("users/$uid/profile/profile_image.jpg")
        storageRef.delete().awaitTask()
      } catch (_: Exception) {}

      val db = getFirestore(context)
      val updates = hashMapOf<String, Any?>(
        "photoUrl" to null,
        "updatedAt" to System.currentTimeMillis()
      )
      db.collection(COLLECTION_USERS).document(uid).set(updates, SetOptions.merge()).awaitTask()

      val authUser = FirebaseAuth.getInstance().currentUser
      if (authUser != null) {
        val changeRequest = UserProfileChangeRequest.Builder().setPhotoUri(null).build()
        authUser.updateProfile(changeRequest).awaitTask()
      }

      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to remove profile image: ${e.message}", e)
      Result.failure(e)
    }
  }
}
