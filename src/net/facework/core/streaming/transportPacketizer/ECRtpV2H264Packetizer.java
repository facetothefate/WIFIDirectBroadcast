package net.facework.core.streaming.transportPacketizer;

import java.io.IOException;
import java.util.Date;

import com.facework.configuration.ServerConf;

import net.facework.core.streaming.transportPacketizer.AbstractPacketizer.Statistics;
import android.os.SystemClock;
import android.util.Log;

public class ECRtpV2H264Packetizer extends AbstractPacketizer implements Runnable{
	public final static String TAG = "EC-RTP H264 Packetizer";

	public final static int MAXPACKETSIZE = ServerConf.MTU;
	
	private int rtphl= ECRtpSocket.RTP_HEADER_LENGTH+ECRtpSocket.EC_HEADER_LENGTH;
	private Thread t = null;
	private int naluLength = 0;
	private long delay = 0;
	private long maxFrame=0;
	//private int fps=30;
	private Statistics stats = new Statistics();

	public ECRtpV2H264Packetizer() throws IOException {
		super();
		// open the EC mode on the RTP
		ecsocket.setEcMode();
		buffer = ecsocket.getBuffer();
	}
	@Override
	public void setMaxFrame(long _maxFrame){
		this.maxFrame=_maxFrame;
	}
	@Override
	public void start() throws IOException {
		if (t == null) {
			t = new Thread(this);
			t.start();
		}
	}

	@Override
	public void stop() {
		try {
			is.close();
		} catch (IOException ignore) {}
		t.interrupt();
		// We wait until the packetizer thread returns
		try {
			t.join();
		} catch (InterruptedException e) {}
		t = null;
	}

	@Override
	public void run() {

		long duration = 0, oldtime = 0;
		Log.d(TAG,"EC-RTP H264 packetizer started !");
		// This will skip the MPEG4 header if this step fails we can't stream anything :(
		// It needs only in the real time mode
		if(this.mode==AbstractPacketizer.DEVICEMODE)
		{	
			try {
				byte buffer[] = new byte[4];
				// Skip all atoms preceding mdat atom
				while (true) {
					while (is.read() != 'm');
					is.read(buffer,0,3);
					if (buffer[0] == 'd' && buffer[1] == 'a' && buffer[2] == 't') break;
				}
			} catch (IOException e) {
				Log.e(TAG,"Couldn't skip mp4 header :/");
				return;
			}
		}

		// Here we read a NAL unit in the input stream and we send it
		try {
			Log.i(TAG,"header length:"+rtphl);
			//ts=1234;
			while (!Thread.interrupted()) {

				// We measure how long it takes to receive the NAL unit from the phone
				oldtime = SystemClock.elapsedRealtime();
				send();
				duration = SystemClock.elapsedRealtime() - oldtime;

				// Calculates the average duration of a NAL unit
				stats.push(duration);
				delay = stats.average();
			}
		} catch (IOException e) {
		} catch (InterruptedException e) {}

		Log.d(TAG,"EC RTP H264 packetizer stopped !");

	}
	
	// Reads a NAL unit in the FIFO and sends it
	// If it is too big, we split it in FU-A units (RFC 3984)
	private void send() throws IOException, InterruptedException {
		int sum = 1, len = 0, type;
		long start= new Date().getTime();
		long end;
		// Read NAL unit length (4 bytes)
		//socket.updateTimestamp(ts);
		fill(rtphl,4);
		naluLength = buffer[rtphl+3]&0xFF | (buffer[rtphl+2]&0xFF)<<8 | (buffer[rtphl+1]&0xFF)<<16 | (buffer[rtphl]&0xFF)<<24;
		//Log.i(TAG,"NAL Unit length:"+naluLength);
		//Log.i(TAG,"NAL Unit length byte:"+toHexString(buffer,rtphl,4));
		if(naluLength==0){Log.i(TAG,"Error NAL unit"); return;}
		// Read NAL unit header (1 byte)
		fill(rtphl, 1);
		// NAL unit type
		type = buffer[rtphl]&0x1F;
		
		//ts += 3600;
		//ts += delay;
		if(this.mode==AbstractPacketizer.DEVICEMODE){
			ts += delay;
			ecsocket.updateTimestamp(ts*90);
		}
		else{
			ts +=90000/videoFps;
			ecsocket.updateTimestamp(ts);
		}
		//ts +=90000/15;
		

		//Log.d(TAG,"- Nal unit length: " + naluLength + " delay: "+delay+" type: "+type);

		// Small NAL unit => Single NAL unit 
		if (naluLength<=MAXPACKETSIZE-rtphl-2) {
			
			len = fill(rtphl+1,  naluLength-1  );
			ecsocket.markNextPacket();
			ecsocket.setPayload(type);
			ecsocket.send(naluLength+rtphl);
			//Log.i(TAG,"packect size :"+ (naluLength+rtphl));
			//Log.d(TAG,"----- Single NAL unit - len:"+len+" header:"+printBuffer(buffer, rtphl,rtphl+3)+" delay: "+delay+" newDelay: "+newDelay);
		}
		// Large NAL unit => Split nal unit 
		else {
			
			// Set FU-A header
			buffer[rtphl+1] = (byte) (buffer[rtphl] & 0x1F);  // FU header type
			buffer[rtphl+1] += 0x80; // Start bit
			// Set FU-A indicator
			buffer[rtphl] = (byte) ((buffer[rtphl] & 0x60) & 0xFF); // FU indicator NRI
			buffer[rtphl] += 28;
			ecsocket.setPayload(28);
			while (sum < naluLength) {
				if ((len = fill(rtphl+2,  naluLength-sum > MAXPACKETSIZE-rtphl-2 ? MAXPACKETSIZE-rtphl-2 : naluLength-sum  ))<0) return; sum += len;
				// Last packet before next NAL
				if (sum >= naluLength) {
					// End bit on
					buffer[rtphl+1] += 0x40;
					ecsocket.markNextPacket();
					ecsocket.updateTimestamp(ts);
				}
				ecsocket.send(len+rtphl+2);
				// Switch start bit
				buffer[rtphl+1] = (byte) (buffer[rtphl+1] & 0x7F); 
				//Log.d(TAG,"----- FU-A unit, sum:"+sum);
			}
		}
		end = new Date().getTime();
		/*if((type==1||type==5)&&this.mode==AbstractPacketizer.FILEMODE){
			long timeleft=1000/videoFps-(start-end)-3;
			if(timeleft>0)
				Thread.sleep(timeleft);
			//Log.d(TAG,"thread sleep:"+timeleft);
		}*/
		//Thread.sleep(30);
	}

	private int fill(int offset,int length) throws IOException {

		int sum = 0, len;

		while (sum<length) {
			len = is.read(buffer, offset+sum, length-sum);
			if (len<0) {
				throw new IOException("End of stream");
			}
			else sum+=len;
		}

		return sum;

	}


}
