package com.facework.tool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

public class BufferedRandomAccessFile extends RandomAccessFile {

public BufferedRandomAccessFile(File file, String mode)
			throws FileNotFoundException {
		super(file, mode);
		// TODO Auto-generated constructor stub
	}
private long bufstartpos;
private boolean bufdirty;
private long bufendpos;
private int bufusedsize;
private long fileendpos;
private long curpos;
private byte[] buf;
private long bufsize;
//  byte read(long pos)：读取当前文件POS位置所在的字节
//  bufstartpos、bufendpos代表BUF映射在当前文件的首/尾偏移地址。
//  curpos指当前类文件指针的偏移地址。
    public byte read(long pos) throws IOException {
        if (pos < this.bufstartpos || pos > this.bufendpos ) {
            this.flushbuf();
            this.seek(pos);
            if ((pos < this.bufstartpos) || (pos > this.bufendpos)) 
                throw new IOException();
        }
        this.curpos = pos;
        return this.buf[(int)(pos - this.bufstartpos)];
    }
// void flushbuf()：bufdirty为真，把buf[]中尚未写入磁盘的数据，写入磁盘。
    private void flushbuf() throws IOException {
        if (this.bufdirty == true) {
            if (super.getFilePointer() != this.bufstartpos) {
                super.seek(this.bufstartpos);
            }
            super.write(this.buf, 0, this.bufusedsize);
            this.bufdirty = false;
        }
    }
// void seek(long pos)：移动文件指针到pos位置，并把buf[]映射填充至POS
    public void seek(long pos) throws IOException {
        if ((pos < this.bufstartpos) || (pos > this.bufendpos)) { // seek pos not in buf
            this.flushbuf();
            if ((pos >= 0) && (pos <= this.fileendpos) && (this.fileendpos != 0)) 
{   // seek pos in file (file length > 0)
            	  long bufbitlen = 0;
				this.bufstartpos =  pos * bufbitlen / bufbitlen;
                this.bufusedsize = this.fillbuf();
            } else if (((pos == 0) && (this.fileendpos == 0)) 
|| (pos == this.fileendpos + 1)) 
{   // seek pos is append pos
                this.bufstartpos = pos;
                this.bufusedsize = 0;
            }
            this.bufendpos = this.bufstartpos + this.bufsize - 1;
        }
        this.curpos = pos;
    }
// int fillbuf()：根据bufstartpos，填充buf[]。
    private int fillbuf() throws IOException {
        super.seek(this.bufstartpos);
        this.bufdirty = false;
        return super.read(this.buf);
    }
}
