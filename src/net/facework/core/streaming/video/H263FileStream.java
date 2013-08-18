package net.facework.core.streaming.video;

import java.io.File;
import java.io.IOException;

import net.facework.core.streaming.FileMediaStream;
import net.facework.core.streaming.rtp.AbstractPacketizer;
import net.facework.core.streaming.rtp.H263Packetizer;

public class H263FileStream  extends FileMediaStream{
	public H263FileStream(File f) throws IOException{
		this.mPacketizer = new H263Packetizer();
		this.mPacketizer.setMode(AbstractPacketizer.FILEMODE);
		this.file=f;
	}

	@Override
	public String generateSessionDescription() throws IllegalStateException,
			IOException {
		// TODO Auto-generated method stub
		return "m=video "+String.valueOf(getDestinationPort())+" RTP/AVP 96\r\n" +
		"b=RR:0\r\n" +
		"a=rtpmap:96 H263-1998/90000\r\n";
	}
	

}
