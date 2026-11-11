package com.lifechat.app

import android.app.Activity
import android.media.AudioManager
import android.Manifest
import android.content.pm.PackageManager
import android.webkit.PermissionRequest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.JavascriptInterface
import android.view.View
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import org.json.JSONObject
import org.json.JSONArray

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var pendingWebPermissionRequest: PermissionRequest? = null
    private val mediaPermissionRequestCode = 4101

    private val db by lazy {
        FirebaseDatabase.getInstance(
            "https://lifechat-46fe1-default-rtdb.firebaseio.com/"
        ).reference
    }

    private var uid: String? = null
    private var activeRoom: String? = null
    private val attempting = mutableSetOf<String>()

    private var waitingListener: ValueEventListener? = null
    private var claimListener: ValueEventListener? = null
    private var messagesListener: ChildEventListener? = null
    private var signalsListener: ChildEventListener? = null
    private var pendingRoomListener: ValueEventListener? = null
    private var pendingRoomRef: DatabaseReference? = null

    @Suppress("DEPRECATION", "SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // تنظیم حالت صوتی مخصوص تماس
        val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isMicrophoneMute = false
        audioManager.isSpeakerphoneOn = true

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

        webView = WebView(this)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.cacheMode = WebSettings.LOAD_DEFAULT
        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                "/assets/",
                WebViewAssetLoader.AssetsPathHandler(this)
            )
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: android.webkit.WebResourceRequest
            ): android.webkit.WebResourceResponse? {
                return assetLoader.shouldInterceptRequest(request.url)
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    val resources = request.resources.filter {
                        it == PermissionRequest.RESOURCE_VIDEO_CAPTURE ||
                        it == PermissionRequest.RESOURCE_AUDIO_CAPTURE
                    }

                    if (resources.isEmpty()) {
                        request.deny()
                        return@runOnUiThread
                    }

                    val missing = mutableListOf<String>()

                    if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in resources &&
                        checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        missing.add(Manifest.permission.CAMERA)
                    }

                    if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in resources &&
                        checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        missing.add(Manifest.permission.RECORD_AUDIO)
                    }

                    if (missing.isEmpty()) {
                        request.grant(resources.toTypedArray())
                    } else {
                        pendingWebPermissionRequest?.deny()
                        pendingWebPermissionRequest = request
                        requestPermissions(missing.toTypedArray(), mediaPermissionRequestCode)
                    }
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                if (pendingWebPermissionRequest == request) {
                    pendingWebPermissionRequest = null
                }
            }
        }
        webView.addJavascriptInterface(FirebaseBridge(), "FirebaseBridge")

        setContentView(webView)
        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html")
    }

    @Deprecated("Deprecated in Android")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == mediaPermissionRequestCode) {
            val request = pendingWebPermissionRequest
            pendingWebPermissionRequest = null

            if (request != null) {
                val allowed = request.resources.filter {
                    when (it) {
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                            checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                        PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        else -> false
                    }
                }

                if (allowed.isNotEmpty()) {
                    request.grant(allowed.toTypedArray())
                } else {
                    request.deny()
                }
            }
        }
    }

    override fun onDestroy() {
        try {
            val audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
            audioManager.mode = AudioManager.MODE_NORMAL
            audioManager.isSpeakerphoneOn = false
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (::webView.isInitialized) {
            webView.destroy()
        }

        super.onDestroy()
    }

    inner class FirebaseBridge {

        private var userStatsListener: ValueEventListener? = null

        @JavascriptInterface
        fun loadUserStats() {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                status("برای دریافت آمار ابتدا وارد شو.")
                return
            }

            val usersRef = db.child("users")
            val myRef = usersRef.child(user.uid)

            myRef.onDisconnect().updateChildren(
                mapOf(
                    "online" to false,
                    "lastSeen" to ServerValue.TIMESTAMP
                )
            )

            myRef.updateChildren(
                mapOf(
                    "name" to "کاربر مهمان",
                    "online" to true,
                    "lastSeen" to ServerValue.TIMESTAMP
                )
            ).addOnFailureListener {
                status("خطا در ثبت وضعیت کاربر: ${it.localizedMessage}")
            }

            userStatsListener?.let {
                usersRef.removeEventListener(it)
            }

            userStatsListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val onlineUsers = snapshot.children.filter {
                        it.child("online").getValue(Boolean::class.java) == true
                    }

                    val usersArray = JSONArray()
                    onlineUsers.forEach {
                        usersArray.put(
                            JSONObject().put(
                                "name",
                                it.child("name").getValue(String::class.java)
                                    ?: "کاربر مهمان"
                            )
                        )
                    }

                    val result = JSONObject()
                        .put("total", snapshot.childrenCount)
                        .put("online", onlineUsers.size)
                        .put("users", usersArray)

                    sendJs("onUserStatsResult", result)
                }

                override fun onCancelled(error: DatabaseError) {
                    status("خطای دریافت آمار کاربران: ${error.message}")
                }
            }

            usersRef.addValueEventListener(userStatsListener!!)
        }

        @JavascriptInterface
        fun signInAnonymously() {
            val auth = FirebaseAuth.getInstance()

            if (auth.currentUser != null) {
                uid = auth.currentUser!!.uid
                sendJs("onFirebaseAuthResult", JSONObject()
                    .put("ok", true).put("uid", uid))
                return
            }

            auth.signInAnonymously().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    uid = task.result?.user?.uid
                    sendJs("onFirebaseAuthResult", JSONObject()
                        .put("ok", true).put("uid", uid))
                } else {
                    sendJs("onFirebaseAuthResult", JSONObject()
                        .put("ok", false)
                        .put("error", task.exception?.localizedMessage ?: "Login failed"))
                }
            }
        }

        @JavascriptInterface
        fun startMatching() {
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null) {
                status("اول وارد LIFE CHAT شو.")
                return
            }

            uid = user.uid
            cleanupListeners()
            activeRoom = null
            attempting.clear()

            val myWaiting = db.child("waiting").child(user.uid)
            myWaiting.onDisconnect().removeValue()

            myWaiting.setValue(mapOf("createdAt" to ServerValue.TIMESTAMP))
                .addOnSuccessListener {
                    status("در حال جستجوی یک دوست...")
                    listenForUsers(user.uid)
                    listenForClaim(user.uid)
                }
                .addOnFailureListener {
                    status("خطا در صف انتظار: ${it.localizedMessage}")
                }
        }

        private fun listenForUsers(myUid: String) {
            waitingListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (activeRoom != null) return

                    val candidates = snapshot.children
                        .mapNotNull { it.key }
                        .filter { it != myUid }
                        .shuffled()

                    candidates.forEach { candidate ->
                        if (activeRoom == null) attemptClaim(myUid, candidate)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    status("خطا در جستجو: ${error.message}")
                }
            }

            db.child("waiting").addValueEventListener(waitingListener!!)
        }

        private fun attemptClaim(myUid: String, candidate: String) {
            if (!attempting.add(candidate)) return

            if (activeRoom != null) {
                attempting.remove(candidate)
                return
            }

            val roomId = db.child("rooms").push().key
            if (roomId == null) {
                attempting.remove(candidate)
                return
            }

            val claim = mapOf(
                "from" to myUid,
                "roomId" to roomId,
                "createdAt" to ServerValue.TIMESTAMP
            )

            db.child("claims").child(candidate).setValue(claim)
                .addOnSuccessListener {
                    val room = mapOf(
                        "targetUid" to candidate,
                        "createdAt" to ServerValue.TIMESTAMP,
                        "members" to mapOf(
                            myUid to true,
                            candidate to true
                        )
                    )

                    db.child("rooms").child(roomId).setValue(room)
                        .addOnSuccessListener {
                            activateRoom(roomId, candidate)
                        }
                        .addOnFailureListener {
                            attempting.remove(candidate)
                            status("ساخت اتاق ناموفق بود: ${it.localizedMessage}")
                        }
                }
                .addOnFailureListener {
                    attempting.remove(candidate)
                    status("ثبت درخواست اتصال ناموفق بود: ${it.localizedMessage}")
                }
        }

        private fun listenForClaim(myUid: String) {
            claimListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists() || activeRoom != null) return

                    val partner = snapshot.child("from").getValue(String::class.java) ?: return
                    val roomId = snapshot.child("roomId").getValue(String::class.java) ?: return

                    watchClaimRoom(myUid, partner, roomId)
                }

                override fun onCancelled(error: DatabaseError) {
                    status("خطای دریافت درخواست اتصال: ${error.message}")
                }
            }

            db.child("claims").child(myUid)
                .addValueEventListener(claimListener!!)
        }

        private fun watchClaimRoom(
            myUid: String,
            partner: String,
            roomId: String,
            retry: Int = 0
        ) {
            if (pendingRoomRef?.key == roomId) return

            pendingRoomRef?.let { ref ->
                pendingRoomListener?.let { ref.removeEventListener(it) }
            }

            val ref = db.child("rooms").child(roomId)
            pendingRoomRef = ref

            pendingRoomListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val isMember = snapshot.child("members")
                        .child(myUid).getValue(Boolean::class.java) == true

                    if (snapshot.exists() && isMember) {
                        ref.removeEventListener(this)
                        pendingRoomListener = null
                        pendingRoomRef = null

                        db.child("claims").child(myUid).removeValue()
                        activateRoom(roomId, partner)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    ref.removeEventListener(this)
                    pendingRoomListener = null
                    pendingRoomRef = null

                    if (activeRoom == null && retry < 10) {
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (activeRoom == null) {
                                watchClaimRoom(myUid, partner, roomId, retry + 1)
                            }
                        }, 700)
                    } else {
                        status("خطای اتاق: ${error.message}")
                    }
                }
            }

            ref.addValueEventListener(pendingRoomListener!!)
        }

        private fun activateRoom(roomId: String, partner: String) {
            if (activeRoom == roomId) return

            activeRoom = roomId
            cleanupQueueListeners()
            uid?.let { db.child("waiting").child(it).removeValue() }

            status("وصل شدی! اکنون می‌توانی پیام بدهی.")
            sendJs("onMatchResult", JSONObject()
                .put("roomId", roomId)
                .put("partnerUid", partner))

            listenForMessages(roomId)
            listenForSignals(roomId)
        }

        private fun listenForMessages(roomId: String) {
            cleanupMessageListener()

            messagesListener = object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val message = JSONObject()
                        .put("id", snapshot.key ?: "")
                        .put("from", snapshot.child("from").getValue(String::class.java) ?: "")
                        .put("text", snapshot.child("text").getValue(String::class.java) ?: "")
                        .put("createdAt", snapshot.child("createdAt").getValue(Long::class.java) ?: 0L)

                    sendJs("onIncomingMessage", message)
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {
                    status("خطای پیام‌ها: ${error.message}")
                }
            }

            db.child("rooms").child(roomId).child("messages")
                .addChildEventListener(messagesListener!!)
        }

        @JavascriptInterface
        fun sendSignal(type: String, payload: String) {
            val roomId = activeRoom
            val user = FirebaseAuth.getInstance().currentUser

            if (roomId == null || user == null) return
            if (type !in listOf("offer", "answer", "candidate", "hangup")) return
            if (payload.length > 20000) return

            val signal = mapOf(
                "from" to user.uid,
                "type" to type,
                "payload" to payload,
                "createdAt" to ServerValue.TIMESTAMP
            )

            db.child("rooms").child(roomId).child("signals")
                .push().setValue(signal)
                .addOnFailureListener {
                    status("ارسال اطلاعات تماس ناموفق بود: ${it.localizedMessage}")
                }
        }

        private fun listenForSignals(roomId: String) {
            signalsListener?.let {
                db.child("rooms").child(roomId).child("signals")
                    .removeEventListener(it)
            }

            signalsListener = object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    val from = snapshot.child("from").getValue(String::class.java) ?: return
                    val type = snapshot.child("type").getValue(String::class.java) ?: return
                    val payload = snapshot.child("payload").getValue(String::class.java) ?: ""

                    if (from == uid) return

                    val data = JSONObject()
                        .put("from", from)
                        .put("type", type)
                        .put("payload", payload)

                    sendJs("onIncomingSignal", data)
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onChildRemoved(snapshot: DataSnapshot) {}
                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
                override fun onCancelled(error: DatabaseError) {
                    status("خطای دریافت اطلاعات تماس: ${error.message}")
                }
            }

            db.child("rooms").child(roomId).child("signals")
                .addChildEventListener(signalsListener!!)
        }

        @JavascriptInterface
        fun sendMessage(text: String) {
            val roomId = activeRoom
            val user = FirebaseAuth.getInstance().currentUser

            if (roomId == null || user == null || text.isBlank()) return
            if (text.length > 1000) {
                status("پیام نباید بیشتر از ۱۰۰۰ حرف باشد.")
                return
            }

            val message = mapOf(
                "from" to user.uid,
                "text" to text.trim(),
                "createdAt" to ServerValue.TIMESTAMP
            )

            db.child("rooms").child(roomId).child("messages")
                .push().setValue(message)
                .addOnFailureListener {
                    status("ارسال پیام ناموفق بود: ${it.localizedMessage}")
                }
        }

        @JavascriptInterface
        fun nextChat() {
            stopInternal()
            val user = FirebaseAuth.getInstance().currentUser ?: return
            db.child("waiting").child(user.uid).removeValue()
                .addOnSuccessListener { startMatching() }
                .addOnFailureListener { status("خطا در شروع دوباره: ${it.localizedMessage}") }
        }

        @JavascriptInterface
        fun stopChat() {
            stopInternal()
            status("اتصال و جستجو متوقف شد.")
        }

        private fun stopInternal() {
            val user = FirebaseAuth.getInstance().currentUser
            cleanupListeners()
            activeRoom = null
            attempting.clear()

            if (user != null) {
                db.child("waiting").child(user.uid).removeValue()
                db.child("claims").child(user.uid).removeValue()
            }

            sendJs("onChatStopped", JSONObject())
        }

        private fun cleanupQueueListeners() {
            uid?.let { user ->
                waitingListener?.let { db.child("waiting").removeEventListener(it) }
                claimListener?.let { db.child("claims").child(user).removeEventListener(it) }
            }
            waitingListener = null
            claimListener = null
        }

        private fun cleanupMessageListener() {
            val room = activeRoom ?: return
            signalsListener?.let {
                db.child("rooms").child(room).child("signals").removeEventListener(it)
            }
            signalsListener = null
            messagesListener?.let {
                db.child("rooms").child(room).child("messages").removeEventListener(it)
            }
            messagesListener = null
        }

        private fun cleanupListeners() {
            cleanupQueueListeners()
            cleanupMessageListener()
            pendingRoomRef?.let { ref ->
                pendingRoomListener?.let { ref.removeEventListener(it) }
            }
            pendingRoomRef = null
            pendingRoomListener = null
        }

        private fun status(message: String) {
            sendJs("onQueueStatus", JSONObject().put("message", message))
        }
    }

    private fun sendJs(function: String, data: JSONObject) {
        val payload = JSONObject.quote(data.toString())
        runOnUiThread {
            if (::webView.isInitialized) {
                webView.evaluateJavascript(
                    "window.$function && window.$function($payload)", null
                )
            }
        }
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
