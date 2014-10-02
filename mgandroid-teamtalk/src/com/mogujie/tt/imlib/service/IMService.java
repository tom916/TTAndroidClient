package com.mogujie.tt.imlib.service;

import java.util.ArrayList;
import java.util.List;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.os.Binder;
import android.os.IBinder;
import android.view.LayoutInflater;

import com.mogujie.tt.R;
import com.mogujie.tt.config.SysConstant;
import com.mogujie.tt.conn.ConnectionManager;
import com.mogujie.tt.imlib.IMActions;
import com.mogujie.tt.imlib.IMContactManager;
import com.mogujie.tt.imlib.IMGroupManager;
import com.mogujie.tt.imlib.IMHeartBeatManager;
import com.mogujie.tt.imlib.IMLoginManager;
import com.mogujie.tt.imlib.IMMessageManager;
import com.mogujie.tt.imlib.IMRecentSessionManager;
import com.mogujie.tt.imlib.IMSession;
import com.mogujie.tt.imlib.db.IMDbManager;
import com.mogujie.tt.imlib.proto.ContactEntity;
import com.mogujie.tt.imlib.proto.GroupEntity;
import com.mogujie.tt.imlib.proto.MessageEntity;
import com.mogujie.tt.imlib.utils.IMUIHelper;
import com.mogujie.tt.log.Logger;
import com.mogujie.tt.ui.activity.MessageActivity;
import com.mogujie.tt.ui.base.TTBaseActivity;
import com.mogujie.tt.ui.utils.IMServiceHelper;
import com.mogujie.tt.ui.utils.IMServiceHelper.OnIMServiceListner;
import com.mogujie.tt.utils.NetworkUtil;

public class IMService extends Service implements OnIMServiceListner {

	private Logger logger = Logger.getLogger(IMService.class);
	private IMServiceBinder binder = new IMServiceBinder();

	public class IMServiceBinder extends Binder {
		public IMService getService() {
			return IMService.this;
		}
	}

	private IMServiceHelper imServiceHelper = new IMServiceHelper();

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		logger.i("IMService onBind");

		return binder;
	}

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		logger.i("IMService onCreate");

		super.onCreate();
	}

	@Override
	public void onDestroy() {
		// TODO Auto-generated method stub
		logger.i("IMService onDestroy");

		imServiceHelper.unregisterActions(getApplicationContext());
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		logger.i("IMService onStartCommand");

		IMLoginManager.instance().setContext(getApplicationContext());
		IMContactManager.instance().setContext(getApplicationContext());
		IMMessageManager.instance().setContext(getApplicationContext());
		IMGroupManager.instance().setContext(getApplicationContext());
		IMRecentSessionManager.instance().setContext(getApplicationContext());

		List<String> actions = new ArrayList<String>();
		actions.add(IMActions.ACTION_MSG_RECV);
		actions.add(ConnectivityManager.CONNECTIVITY_ACTION);
		imServiceHelper.registerActions(getApplicationContext(), actions,
				IMServiceHelper.INTENT_NO_PRIORITY, this);

		// todo eric it makes debug difficult
		// return START_STICKY;

		return START_NOT_STICKY;
	}

	public IMLoginManager getLoginManager() {
		logger.d("getLoginManager");

		return IMLoginManager.instance();
	}

	public IMContactManager getContactManager() {
		logger.d("getContactManager");

		return IMContactManager.instance();
	}

	public IMMessageManager getMessageManager() {
		logger.d("getMessageManager");

		return IMMessageManager.instance();
	}

	public IMHeartBeatManager getHeartBeatManager() {
		logger.d("getHeartBeatManager");

		return IMHeartBeatManager.instance();
	}

	public IMDbManager getDbManager() {
		logger.d("getDbManager");

		return IMDbManager.instance(getApplicationContext());
	}

	public IMGroupManager getGroupManager() {
		logger.d("getGroupManager");

		return IMGroupManager.instance();
	}

	public IMRecentSessionManager getRecentSessionManager() {
		logger.d("getRecentSessionManager");

		return IMRecentSessionManager.instance();
	}

	@Override
	public void onAction(String action, Intent intent,
			BroadcastReceiver broadcastReceiver) {
		// TODO Auto-generated method stub

		if (action.equals(IMActions.ACTION_MSG_RECV)) {
			logger.d("notification#recv unhandled message");

			logger.d("notification#onMsgRecv");
			String sessionId = intent
					.getStringExtra(SysConstant.SESSION_ID_KEY);
			String msgId = intent.getStringExtra(SysConstant.MSG_ID_KEY);
			logger.d("notification#msg no one handled, sessionId:%s, msgId:%s",
					sessionId, msgId);

			MessageEntity msg = getMessageManager().getUnreadMsg(sessionId,
					msgId);
			if (msg == null) {
				logger.e("chat#can't get unread msg");
				return;
			}

			updateRecentList(msg);

			showInNotificationBar(msg, sessionId, msg.sessionType);
		} else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
			if (NetworkUtil.isNetWorkAvalible(getApplicationContext())) {
				IMLoginManager.instance().relogin();
			}
		}

	}

	private void updateRecentList(MessageEntity msg) {
		logger.d("notification#updateRecentList");

		IMRecentSessionManager recentSessionMg = getRecentSessionManager();

		List<MessageEntity> msgList = new ArrayList<MessageEntity>();
		msgList.add(msg);
		recentSessionMg.batchUpdate(msgList);
	}

	private String getMsgNotificationId(MessageEntity msg) {
		ContactEntity contact = getContactManager().findContact(msg.fromId);
		if (contact == null) {
			logger.e("notification#contact is null, id:%s", msg.fromId);
			return msg.fromId;
		}

		if (msg.isGroupMsg()) {
			String groupId = msg.toId;
			GroupEntity group = getGroupManager().findGroup(groupId);
			if (group == null) {
				logger.e("notification#no group find by id:%s", groupId);
				return groupId;
			}

			return String.format("%s[%s]", msg.fromId, group.name);
		} else if (msg.isP2PMsg()) {
			return String.format("[%s]", msg.fromId);
		}

		// so the UI would reflect the wrong info
		return msg.fromId;
	}

	private String getNotificationContent(MessageEntity msg) {
		// todo eric i18n
		if (msg.isTextType()) {
			return msg.getText();
		} else if (msg.isAudioType()) {
			return "[语音]";
		} else if (msg.isAudioType()) {
			return "[图片]";
		} else {
			return "错误消息图片";
		}
	}

	private String getRollingText(MessageEntity msg, boolean noName) {
		String msgContent = getNotificationContent(msg);
		String contactName = msg.fromId;
		ContactEntity contact = getContactManager().findContact(msg.fromId);
		if (contact == null) {
			logger.e("notification#no contact id:%s", msg.fromId);
		} else {
			contactName = contact.name;
		}

		if (noName) {
			return msgContent;
		} else {
			return String.format("%s: %s", contactName, msgContent);
		}
	}

	private String getNotificationTitle(MessageEntity msg) {
		if (msg.isGroupMsg()) {
			GroupEntity group = getGroupManager().findGroup(msg.toId);
			if (group == null) {
				logger.e("notification#no such group id:%s", msg.toId);
				return "no such group:" + msg.toId;
			}

			return group.name;
		} else if (msg.isP2PMsg()) {
			ContactEntity contact = getContactManager().findContact(msg.fromId);
			if (contact == null) {
				logger.e("notification#no such contact id:%s", msg.fromId);
				return "no such contact:" + msg.fromId;
			}

			return contact.name;
		}

		return "wrong message type:" + msg.fromId + " " + msg.toId;
	}

	private String getNotificationContentText(MessageEntity msg) {
		if (msg.isGroupMsg()) {

			return getRollingText(msg, false);
		} else {
			return getRollingText(msg, true);
		}
	}
	
	private int getNotificationIconResId(MessageEntity msg) {
		if (msg.isGroupMsg()) {
			GroupEntity group = getGroupManager().findGroup(msg.toId);
			if (group == null) {
				logger.e("notification#no group find by id:%s", msg.toId);
				return IMUIHelper.getDefaultAvatarResId(IMSession.SESSION_GROUP);
			}
			
			return IMUIHelper.getDefaultAvatarResId(group.type);
		} else {
			return IMUIHelper.getDefaultAvatarResId(IMSession.SESSION_P2P);
		}
	}

	private Bitmap getNotificationLargeIcon(MessageEntity msg) {
		int resId = getNotificationIconResId(msg);
		
		//todo eric should release it explicitly
		return BitmapFactory.decodeResource(getResources(), resId);
		 
	}

	private void showInNotificationBar(MessageEntity msg, String sessionId,
			int sessionType) {
		logger.d("notification#showInNotificationBar msg:%s, sessionId:%s, sessionType:%d", msg, sessionId, sessionType);

		NotificationManager notifyMgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		if (notifyMgr == null) {
			return;
		}

		Notification notification = new Notification();

//		new Notification.Builder(getApplicationContext())
//				.setContentTitle(getNotificationTitle(msg))
//				.setContentText(getNotificationContentText(msg)).setSmallIcon(R.drawable.tt_default_user_portrait_corner) // todo eric use small icon
//				.setLargeIcon(getNotificationLargeIcon()
//				.
//				build();

		notification.icon = IMUIHelper.getDefaultAvatarResId(msg.sessionType);

		long[] vibrate = { 1000, 1000, 1000, 1000, 1000 };
		notification.vibrate = vibrate;

		notification.when = System.currentTimeMillis();

		notification.flags |= Notification.FLAG_AUTO_CANCEL;

		// rolling text
		notification.tickerText = getRollingText(msg, false);
		notification.defaults = Notification.DEFAULT_SOUND;

		Intent intent = new Intent(this, MessageActivity.class);
		IMUIHelper.setSessionInIntent(intent, sessionId, sessionType);

		PendingIntent pendingIntent = PendingIntent.getActivity(
				getApplicationContext(), 0, intent, PendingIntent.FLAG_ONE_SHOT);
		notification.setLatestEventInfo(getApplicationContext(),
				getNotificationTitle(msg),
				getNotificationContentText(msg), pendingIntent);
		notifyMgr.notify(0, notification);
	}

	@Override
	public void onIMServiceConnected() {
		// TODO Auto-generated method stub

	}

}