package com.facework.mp4;

//this class is the containner of the chunk offset;
public class MP4ChunkOffsetGroup {
	public final static int VIDEO= 0x01;
	public final static int AUDIO= 0x02;
	private byte[] offset=null;
	private int[] flag;
	private int index=0;
	public byte[] readOffset(){
		byte[] temp=null;
		temp[0]=offset[index*4+0];
		temp[1]=offset[index*4+1];
		temp[2]=offset[index*4+2];
		temp[3]=offset[index*4+3];
		return temp;
	}
	public int readFlag(){
		return flag[index];
	}
	public void moveToNextUnit(){
		index++;
	}
	public void setUnit(int _flag,byte[] _offset){
		flag[index]=_flag;
		offset[index*4+0]=_offset[0];
		offset[index*4+1]=_offset[1];
		offset[index*4+2]=_offset[2];
		offset[index*4+3]=_offset[3];
	}
}
