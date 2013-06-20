package com.facework.shell;


import com.example.wifidirectbroadcast.R;

import net.facework.core.streaming.SessionManager;
import net.facework.core.streaming.misc.RtspServer;
import android.os.Bundle;
import android.os.IBinder;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.util.Log;
import android.view.Menu;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.Toast;

public class MainActivity extends Activity {

	private RtspServer mRtspServer;
	private SurfaceView mSurfaceView;
	private SurfaceHolder mSurfaceHolder;
	public void log(String s) {
		Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show();
	}
	private ServiceConnection mRtspServiceConnection = new ServiceConnection() {

		@Override
		public void onServiceConnected(ComponentName name, IBinder service) {
			mRtspServer = (RtspServer) ((RtspServer.LocalBinder)service).getService();
			mRtspServer.addCallbackListener(mRtspCallbackListener);
			Log.i("RTSP server","try to start RTSP server");
			mRtspServer.start();
		}

		@Override
		public void onServiceDisconnected(ComponentName name) {}

	};
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		this.startService(new Intent(this.getApplicationContext() ,RtspServer.class));
		mSurfaceView = (SurfaceView)findViewById(R.id.preview);
		mSurfaceHolder = mSurfaceView.getHolder();
		// We still need this line for backward compatibility reasons with android 2
		mSurfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		SessionManager.getManager().setSurfaceHolder(mSurfaceHolder, false);
		Log.i("RTSP server","try to start RTSP server service");
	}
	@Override
	public void onStart() {
		super.onStart();
		bindService(new Intent(this,RtspServer.class), mRtspServiceConnection, Context.BIND_AUTO_CREATE);
	}
	@Override
	public void onStop() {
		super.onStop();
		unbindService(mRtspServiceConnection);
	}
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	private RtspServer.CallbackListener mRtspCallbackListener = new RtspServer.CallbackListener() {

		@Override
		public void onError(RtspServer server, Exception e, int error) {
			// We alert the user that the port is already used by another app.
			if (error == RtspServer.ERROR_BIND_FAILED) {
				new AlertDialog.Builder(MainActivity.this)
				.setTitle(R.string.port_used)
				.setMessage(getString(R.string.bind_failed, "RTSP"))
				.show();
			}
		}

	};	

}
