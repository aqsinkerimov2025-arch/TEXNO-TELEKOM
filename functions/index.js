const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

exports.notifyAdminOfFeedback = onDocumentCreated(
  { document: "admin_feedback/{messageId}", region: "europe-west1" },
  async (event) => {
    const data = event.data?.data();
    if (!data) return;

    const tokenRef = getFirestore().collection("admin_push_tokens").doc("FflCErahavNIb8ZXsvHIyrxZYZH3");
    const tokenSnap = await tokenRef.get();
    if (!tokenSnap.exists) {
      console.warn("Admin FCM token is not registered yet.");
      return;
    }

    const token = tokenSnap.get("token");
    if (typeof token !== "string" || !token) return;

    const sender = (data.nickname || "İstifadəçi").toString().slice(0, 40);
    const isVoice = Boolean(data.audio_url);
    const preview = isVoice ? "Sizə yeni səsli mesaj göndərildi." :
      (data.text || "Sizə yeni mesaj göndərildi.").toString().slice(0, 140);

    try {
      await getMessaging().send({
        token,
        notification: {
          title: "TEXNO-TELEKOM • Yeni mesaj",
          body: sender + ": " + preview
        },
        data: {
          type: "admin_feedback",
          open_admin_feedback: "true"
        },
        android: {
          priority: "high",
          notification: {
            channelId: "admin_feedback_messages",
            sound: "default"
          }
        }
      });
    } catch (error) {
      console.error("Could not send admin feedback push notification:", error);
      if (error.code === "messaging/registration-token-not-registered" ||
          error.code === "messaging/invalid-registration-token") {
        await tokenRef.delete();
      }
    }
  }
);