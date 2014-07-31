package com.facework.configuration;

import java.net.InetAddress;

public class ServerConf {
	//config of tunnel switch
	public static boolean TUNNEL= false;
	public final static boolean OFF = false;
	public final static boolean ON = true;
	
	//MTU
	public static final int MTU = 1500;
	
	//simulation loss of input
	public static int CLIENT_LOSS=0;
	public static int SERVER_LOSS=0;
	
	//use RTP or use EC-RTP
	public static int TRANS = 0x01 ;
	public final static int RTP = 0x01;
	public final static int EC_RTP = 0x02;
	public final static int EC_RTP_V2 = 0x03;
	
	//Tunnel ip address
	public static InetAddress TUNNEL_IP;
	public final static boolean TUNNEL_ON_PLAYER = true;
	
	//EC-RTP
	public static int BLOCK_SIZE=50;
	public static int ADDTIONAL_PACKETS_NUMBER=60;
	
	public static int XOR_REGION=10;

}
