package com.mlst.leapdroid;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.widget.Toast;

public class MessengerService extends Service {
	ArrayList<Messenger> mClients = new ArrayList<Messenger>();
	int mValue = 0;
	private static final float alpha = 0.04f;
	private float[] positionOutput = new float[3];
	private float[] rotationOutput = new float[3];

	/**
	 * Command to the service to register a client, receiving callbacks from the
	 * service. The Message's replyTo field must be a Messenger of the client
	 * where callbacks should be sent.
	 */
	static final int MSG_REGISTER_CLIENT = 1;

	/**
	 * Command to the service to unregister a client, ot stop receiving
	 * callbacks from the service. The Message's replyTo field must be a
	 * Messenger of the client as previously given with MSG_REGISTER_CLIENT.
	 */
	static final int MSG_UNREGISTER_CLIENT = 2;

	/**
	 * Command to service to set a new value. This can be sent to the service to
	 * supply a new value, and will be sent by the service to any registered
	 * clients with the new value.
	 */
	static final int MSG_SET_VALUE = 3;

	/**
	 * Handler of incoming messages from clients.
	 */
	class IncomingHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MSG_REGISTER_CLIENT:
				mClients.add(msg.replyTo);
				break;
			case MSG_UNREGISTER_CLIENT:
				mClients.remove(msg.replyTo);
				break;
			case MSG_SET_VALUE:
				Object obj = msg.obj;
				for (int i = mClients.size() - 1; i >= 0; i--) {
					try {
						mClients.get(i).send(
								Message.obtain(null, MSG_SET_VALUE, obj));
					} catch (RemoteException e) {
						// The client is dead. Remove it from the list;
						// we are going through the list from back to front
						// so this is safe to do inside the loop.
						mClients.remove(i);
					}
				}
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
	private ServerThread thread;

	/**
	 * When binding to the service, we return an interface to our messenger for
	 * sending messages to the service.
	 */
	@Override
	public IBinder onBind(Intent intent) {
		Log.d("TAG", "starting thread....");

		thread = new ServerThread();
		new Thread(thread).start();

		return mMessenger.getBinder();
	}

	@Override
	public void onDestroy() {
		if (thread != null) {
			thread.stopThread();
		}
		super.onDestroy();
	}

	class ServerThread implements Runnable {
		private boolean keepMonitoring = true;

		@Override
		public void run() {
			Socket s = null;

			try {
				Log.d("TAG", "connecting to server");
				s = new Socket("192.168.0.25", 1337);
				Log.d("TAG", "connected to server:: " + s.isConnected());

				BufferedReader input = new BufferedReader(
						new InputStreamReader(s.getInputStream()));
				String line = "";

				while (keepMonitoring) {
					line = input.readLine();
					if (line.contains(":")) {
						String[] rots = line.split(":")[0].split(",");
						String[] vals = line.split(":")[1].split(",");
						float[] inputPosition = { Float.parseFloat(vals[0]),
								Float.parseFloat(vals[1]),
								Float.parseFloat(vals[2]) };
						float[] smoothVals = applyLPF(inputPosition,
								positionOutput);

						inputPosition = new float[] {
								Float.parseFloat(rots[0]),
								Float.parseFloat(rots[1]),
								Float.parseFloat(rots[2]) };

						float[] smoothRots = applyLPF(inputPosition,
								rotationOutput);
						float x = smoothVals[0];
						float y = smoothVals[1];

						if (y < 200) {
							y = 200 - y;
						} else {
							y = y - 200;
						}

						float z = smoothVals[2];

						float[] obj = { x, y, z, smoothRots[0] };
						mMessenger.send(Message
								.obtain(null, MSG_SET_VALUE, obj));

					}

				}
				// Close connection

			} catch (UnknownHostException e1) {
				e1.printStackTrace();
			} catch (IOException e1) {
				e1.printStackTrace();
			} catch (RemoteException e) {
				e.printStackTrace();
			} finally {
				try {
					s.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

		}

		private float[] applyLPF(float[] input, float[] output) {
			if (output == null)
				return input;

			for (int i = 0; i < input.length; i++) {
				output[i] = output[i] + alpha * (input[i] - output[i]);
			}
			return output;
		}

		public void stopThread() {
			keepMonitoring = false;
		}
	}

}