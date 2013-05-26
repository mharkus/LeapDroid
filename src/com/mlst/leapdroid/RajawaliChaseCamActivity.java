package com.mlst.leapdroid;

import rajawali.math.Number3D;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.view.Gravity;
import android.view.Window;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class RajawaliChaseCamActivity extends RajawaliExampleActivity implements
		OnSeekBarChangeListener {
	private RajawaliChaseCamRenderer mRenderer;
	private SeekBar mSeekBarX, mSeekBarY, mSeekBarZ;
	private Number3D mCameraOffset;
	private Messenger mService = null;
	private boolean mIsBound;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);

		doBindService();

		mMultisamplingEnabled = false;
		mCameraOffset = new Number3D();
		super.onCreate(savedInstanceState);

		mRenderer = new RajawaliChaseCamRenderer(this);
		mRenderer.setSurfaceView(mSurfaceView);
		mRenderer.setUsesCoverageAa(mUsesCoverageAa);
		super.setRenderer(mRenderer);

		initLoader();
	}

	@Override
	protected void onDestroy() {
		doUnbindService();

		super.onDestroy();
	}

	public void onProgressChanged(SeekBar seekBar, int progress,
			boolean fromUser) {
		mCameraOffset.setAll(mSeekBarX.getProgress() - 10,
				mSeekBarY.getProgress() - 10, mSeekBarZ.getProgress());
		mRenderer.setCameraOffset(mCameraOffset);
	}

	public void onStartTrackingTouch(SeekBar seekBar) {
	}

	public void onStopTrackingTouch(SeekBar seekBar) {
	}

	private float prevX;
	private float prevY;
	private float prevZ;

	/**
	 * Handler of incoming messages from service.
	 */
	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MessengerService.MSG_SET_VALUE:
				float[] reading = (float[]) msg.obj;
				mRenderer.setValues(reading[0], reading[1], reading[2], reading[3]);
				break;
			default:
				super.handleMessage(msg);
			}
		}
	}

	/**
	 * Target we publish for clients to send messages to IncomingHandler.
	 */
	final Messenger mMessenger = new Messenger(new IncomingHandler());

	/**
	 * Class for interacting with the main interface of the service.
	 */
	private ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			// This is called when the connection with the service has been
			// established, giving us the service object we can use to
			// interact with the service. We are communicating with our
			// service through an IDL interface, so get a client-side
			// representation of that from the raw service object.
			mService = new Messenger(service);
			Log.d("TAG", "Attached.");

			// We want to monitor the service for as long as we are
			// connected to it.
			try {
				Message msg = Message.obtain(null,
						MessengerService.MSG_REGISTER_CLIENT);
				msg.replyTo = mMessenger;
				mService.send(msg);
			} catch (RemoteException e) {
				// In this case the service has crashed before we could even
				// do anything with it; we can count on soon being
				// disconnected (and then reconnected if it can be restarted)
				// so there is no need to do anything here.
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			// This is called when the connection with the service has been
			// unexpectedly disconnected -- that is, its process crashed.
			mService = null;
			Log.d("TAG", "Disconnected.");
		}
	};

	void doBindService() {
		// Establish a connection with the service. We use an explicit
		// class name because there is no reason to be able to let other
		// applications replace our component.
		bindService(new Intent(RajawaliChaseCamActivity.this,
				MessengerService.class), mConnection, Context.BIND_AUTO_CREATE);
		mIsBound = true;
		Log.d("TAG", "Binding.");
	}

	void doUnbindService() {
		if (mIsBound) {
			// If we have received the service, and hence registered with
			// it, then now is the time to unregister.
			if (mService != null) {
				try {
					Message msg = Message.obtain(null,
							MessengerService.MSG_UNREGISTER_CLIENT);
					msg.replyTo = mMessenger;
					mService.send(msg);
				} catch (RemoteException e) {
					// There is nothing special we need to do if the service
					// has crashed.
				}
			}

			// Detach our existing connection.
			unbindService(mConnection);
			mIsBound = false;
			Log.d("TAG", "Unbinding.");
		}
	}
}
