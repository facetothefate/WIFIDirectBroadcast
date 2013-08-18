/*
 * Copyright (C) 2011-2013 GUIGUI Simon, fyhertz@gmail.com
 * 
 * This file is part of Spydroid (http://code.google.com/p/spydroid-ipcamera/)
 * 
 * Spydroid is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This source code is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this source code; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package net.facework.core.streaming;

import java.io.IOException;
import java.net.InetAddress;
import java.util.Random;

import net.facework.core.streaming.rtp.AbstractPacketizer;
import android.media.MediaRecorder;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;

/**
 *  A MediaRecorder that streams what it records using a packetizer from the rtp package.
 *  You can't use this class directly !
 */
public abstract class MediaStream extends MediaRecorder implements Stream {

	protected static final String TAG = "MediaStream";

	/** MediaStream forwards data to a packetizer through a LocalSocket. */
	public static final int MODE_STREAMING = 0;
	
	/** MediaStream will just act as a regular MediaRecorder. */
	public static final int MODE_DEFAULT = 1;

	private int mSocketId;

	protected AbstractPacketizer mPacketizer = null;

	protected boolean mStreaming = false, mModeDefaultWasUsed = false;
	protected int mode = MODE_STREAMING;

	private LocalServerSocket mLss = null;
	private LocalSocket mReceiver, mSender = null;

	public MediaStream() {
		super();

		for (int i=0;i<10;i++) {
			try {
				mSocketId = new Random().nextInt();
				mLss = new LocalServerSocket("net.majorkernelpanic.librtp-"+mSocketId);
				break;
			} catch (IOException e1) {}
		}

	}

	/** Sets the destination UDP packets will be sent to. **/
	@Override
	public void setDestination(InetAddress dest, int dport) {
		this.mPacketizer.setDestination(dest, dport);
	}

	/** 
	 * Sets the Time To Live of the underlying {@link net.facework.core.streaming.rtp.RtpSocket}. 
	 * @throws IOException 
	 **/
	@Override
	public void setTimeToLive(int ttl) throws IOException {
		this.mPacketizer.setTimeToLive(ttl);
	}

	/** Gets the destination port of the stream. */
	@Override
	public int getDestinationPort() {
		return this.mPacketizer.getRtpSocket().getPort();
	}

	/** Gets the source port of UDP packets. */
	@Override
	public int getLocalPort() {
		return this.mPacketizer.getRtpSocket().getLocalPort();
	}

	/**
	 * Sets the mode of the {@link MediaStreame}.
	 * If the mode is set to {@link #MODE_STREAMING}, video is forwarded to a UDP socket,
	 * and if the mode is {@link #MODE_DEFAULT}, video is recorded in a file.
	 * @param mode Either {@link #MODE_STREAMING} or {@link #MODE_DEFAULT} 
	 * @throws IllegalStateException
	 */
	public void setMode(int mode) throws IllegalStateException {
		if (!mStreaming) {
			this.mode = mode;
			if (mode == MODE_DEFAULT) mModeDefaultWasUsed = true;
		}
		else {
			throw new IllegalStateException("You can't call setMode() while streaming !");
		}
	}

	/**
	 * Returns the packetizer associated with the {@link MediaStream}.
	 * @return The packetizer
	 */
	public AbstractPacketizer getPacketizer() { 
		return mPacketizer;
	}

	/**
	 * Indicates if the {@link MediaStream} is streaming.
	 * @return A boolean indicating if the {@link MediaStream} is streaming
	 */
	@Override
	public boolean isStreaming() {
		return mStreaming;
	}

	/** Prepares the stream. */
	@Override
	public void prepare() throws IllegalStateException,IOException {
		if (mode==MODE_STREAMING) {
			createSockets();
			// We write the ouput of the camera in a local socket instead of a file !			
			// This one little trick makes streaming feasible quiet simply: data from the camera
			// can then be manipulated at the other end of the socket
			setOutputFile(mSender.getFileDescriptor());
		}
		super.prepare();
	}

	/** Starts the stream. */
	@Override
	public void start() throws IllegalStateException {
		super.start();
		try {
			if (mode==MODE_STREAMING) {
				// receiver.getInputStream contains the data from the camera
				// the packetizer encapsulates this stream in an RTP stream and send it over the network
				mPacketizer.setInputStream(mReceiver.getInputStream());
				mPacketizer.start();
			}
			mStreaming = true;
		} catch (IOException e) {
			super.stop();
			throw new IllegalStateException("Something happened with the local sockets :/ Start failed");
		} catch (NullPointerException e) {
			super.stop();
			throw new IllegalStateException("setPacketizer() should be called before start(). Start failed");
		}
	}

	/** Stops the stream. */
	@Override
	public void stop() {
		if (mStreaming) {
			try {
				super.stop();
			}
			catch (Exception ignore) {}
			finally {
				super.reset();
				closeSockets();
				if (mode==MODE_STREAMING) mPacketizer.stop();
				mStreaming = false;
			}
		}
	}

	/**
	 * Returns the SSRC of the underlying {@link net.facework.core.streaming.rtp.RtpSocket}.
	 * @return the SSRC of underlying RTP socket
	 */
	@Override
	public int getSSRC() {
		return getPacketizer().getRtpSocket().getSSRC();
	}

	@Override
	public abstract String generateSessionDescription()  throws IllegalStateException, IOException;

	private void createSockets() throws IOException {
		mReceiver = new LocalSocket();
		mReceiver.connect( new LocalSocketAddress("net.majorkernelpanic.librtp-" + mSocketId ) );
		mReceiver.setReceiveBufferSize(500000);
		mSender = mLss.accept();
		mSender.setSendBufferSize(500000); 
	}

	private void closeSockets() {
		try {
			mSender.close();
			mReceiver.close();
		} catch (Exception ignore) {}
	}

	/** 
	 * Releases ressources associated with the stream. 
	 * The object can not be used anymore when this function is called. 
	 **/
	@Override
	public void release() {
		stop();
		try {
			mLss.close();
		}
		catch (Exception ignore) {}
		super.release();
	}

}
