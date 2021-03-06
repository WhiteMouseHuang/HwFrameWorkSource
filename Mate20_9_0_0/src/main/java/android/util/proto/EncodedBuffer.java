package android.util.proto;

import android.net.wifi.WifiEnterpriseConfig;
import android.net.wifi.WifiNetworkScoreCache;
import android.os.BatteryStats;
import android.util.Log;
import java.util.ArrayList;

public final class EncodedBuffer {
    private static final String TAG = "EncodedBuffer";
    private int mBufferCount;
    private final ArrayList<byte[]> mBuffers;
    private final int mChunkSize;
    private int mReadBufIndex;
    private byte[] mReadBuffer;
    private int mReadIndex;
    private int mReadLimit;
    private int mReadableSize;
    private int mWriteBufIndex;
    private byte[] mWriteBuffer;
    private int mWriteIndex;

    public EncodedBuffer() {
        this(0);
    }

    public EncodedBuffer(int chunkSize) {
        this.mBuffers = new ArrayList();
        this.mReadLimit = -1;
        this.mReadableSize = -1;
        if (chunkSize <= 0) {
            chunkSize = 8192;
        }
        this.mChunkSize = chunkSize;
        this.mWriteBuffer = new byte[this.mChunkSize];
        this.mBuffers.add(this.mWriteBuffer);
        this.mBufferCount = 1;
    }

    public void startEditing() {
        this.mReadableSize = (this.mWriteBufIndex * this.mChunkSize) + this.mWriteIndex;
        this.mReadLimit = this.mWriteIndex;
        this.mWriteBuffer = (byte[]) this.mBuffers.get(0);
        this.mWriteIndex = 0;
        this.mWriteBufIndex = 0;
        this.mReadBuffer = this.mWriteBuffer;
        this.mReadBufIndex = 0;
        this.mReadIndex = 0;
    }

    public void rewindRead() {
        this.mReadBuffer = (byte[]) this.mBuffers.get(0);
        this.mReadBufIndex = 0;
        this.mReadIndex = 0;
    }

    public int getReadableSize() {
        return this.mReadableSize;
    }

    public int getReadPos() {
        return (this.mReadBufIndex * this.mChunkSize) + this.mReadIndex;
    }

    public void skipRead(int amount) {
        if (amount < 0) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("skipRead with negative amount=");
            stringBuilder.append(amount);
            throw new RuntimeException(stringBuilder.toString());
        } else if (amount != 0) {
            if (amount <= this.mChunkSize - this.mReadIndex) {
                this.mReadIndex += amount;
            } else {
                amount -= this.mChunkSize - this.mReadIndex;
                this.mReadIndex = amount % this.mChunkSize;
                if (this.mReadIndex == 0) {
                    this.mReadIndex = this.mChunkSize;
                    this.mReadBufIndex += amount / this.mChunkSize;
                } else {
                    this.mReadBufIndex += 1 + (amount / this.mChunkSize);
                }
                this.mReadBuffer = (byte[]) this.mBuffers.get(this.mReadBufIndex);
            }
        }
    }

    public byte readRawByte() {
        if (this.mReadBufIndex > this.mBufferCount || (this.mReadBufIndex == this.mBufferCount - 1 && this.mReadIndex >= this.mReadLimit)) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("Trying to read too much data mReadBufIndex=");
            stringBuilder.append(this.mReadBufIndex);
            stringBuilder.append(" mBufferCount=");
            stringBuilder.append(this.mBufferCount);
            stringBuilder.append(" mReadIndex=");
            stringBuilder.append(this.mReadIndex);
            stringBuilder.append(" mReadLimit=");
            stringBuilder.append(this.mReadLimit);
            throw new IndexOutOfBoundsException(stringBuilder.toString());
        }
        if (this.mReadIndex >= this.mChunkSize) {
            this.mReadBufIndex++;
            this.mReadBuffer = (byte[]) this.mBuffers.get(this.mReadBufIndex);
            this.mReadIndex = 0;
        }
        byte[] bArr = this.mReadBuffer;
        int i = this.mReadIndex;
        this.mReadIndex = i + 1;
        return bArr[i];
    }

    public long readRawUnsigned() {
        int bits = 0;
        long result = 0;
        while (true) {
            byte b = readRawByte();
            result |= ((long) (b & 127)) << bits;
            if ((b & 128) == 0) {
                return result;
            }
            bits += 7;
            if (bits > 64) {
                StringBuilder stringBuilder = new StringBuilder();
                stringBuilder.append("Varint too long -- ");
                stringBuilder.append(getDebugString());
                throw new ProtoParseException(stringBuilder.toString());
            }
        }
    }

    public int readRawFixed32() {
        return (((readRawByte() & 255) | ((readRawByte() & 255) << 8)) | ((readRawByte() & 255) << 16)) | ((readRawByte() & 255) << 24);
    }

    private void nextWriteBuffer() {
        this.mWriteBufIndex++;
        if (this.mWriteBufIndex >= this.mBufferCount) {
            this.mWriteBuffer = new byte[this.mChunkSize];
            this.mBuffers.add(this.mWriteBuffer);
            this.mBufferCount++;
        } else {
            this.mWriteBuffer = (byte[]) this.mBuffers.get(this.mWriteBufIndex);
        }
        this.mWriteIndex = 0;
    }

    public void writeRawByte(byte val) {
        if (this.mWriteIndex >= this.mChunkSize) {
            nextWriteBuffer();
        }
        byte[] bArr = this.mWriteBuffer;
        int i = this.mWriteIndex;
        this.mWriteIndex = i + 1;
        bArr[i] = val;
    }

    public static int getRawVarint32Size(int val) {
        if ((val & WifiNetworkScoreCache.INVALID_NETWORK_SCORE) == 0) {
            return 1;
        }
        if ((val & -16384) == 0) {
            return 2;
        }
        if ((-2097152 & val) == 0) {
            return 3;
        }
        if ((-268435456 & val) == 0) {
            return 4;
        }
        return 5;
    }

    public void writeRawVarint32(int val) {
        while ((val & WifiNetworkScoreCache.INVALID_NETWORK_SCORE) != 0) {
            writeRawByte((byte) ((val & 127) | 128));
            val >>>= 7;
        }
        writeRawByte((byte) val);
    }

    public static int getRawZigZag32Size(int val) {
        return getRawVarint32Size(zigZag32(val));
    }

    public void writeRawZigZag32(int val) {
        writeRawVarint32(zigZag32(val));
    }

    public static int getRawVarint64Size(long val) {
        if ((-128 & val) == 0) {
            return 1;
        }
        if ((-16384 & val) == 0) {
            return 2;
        }
        if ((-2097152 & val) == 0) {
            return 3;
        }
        if ((-268435456 & val) == 0) {
            return 4;
        }
        if ((-34359738368L & val) == 0) {
            return 5;
        }
        if ((-4398046511104L & val) == 0) {
            return 6;
        }
        if ((-562949953421312L & val) == 0) {
            return 7;
        }
        if ((BatteryStats.STEP_LEVEL_MODIFIED_MODE_MASK & val) == 0) {
            return 8;
        }
        if ((Long.MIN_VALUE & val) == 0) {
            return 9;
        }
        return 10;
    }

    public void writeRawVarint64(long val) {
        while ((-128 & val) != 0) {
            writeRawByte((byte) ((int) ((127 & val) | 128)));
            val >>>= 7;
        }
        writeRawByte((byte) ((int) val));
    }

    public static int getRawZigZag64Size(long val) {
        return getRawVarint64Size(zigZag64(val));
    }

    public void writeRawZigZag64(long val) {
        writeRawVarint64(zigZag64(val));
    }

    public void writeRawFixed32(int val) {
        writeRawByte((byte) val);
        writeRawByte((byte) (val >> 8));
        writeRawByte((byte) (val >> 16));
        writeRawByte((byte) (val >> 24));
    }

    public void writeRawFixed64(long val) {
        writeRawByte((byte) ((int) val));
        writeRawByte((byte) ((int) (val >> 8)));
        writeRawByte((byte) ((int) (val >> 16)));
        writeRawByte((byte) ((int) (val >> 24)));
        writeRawByte((byte) ((int) (val >> 32)));
        writeRawByte((byte) ((int) (val >> 40)));
        writeRawByte((byte) ((int) (val >> 48)));
        writeRawByte((byte) ((int) (val >> 56)));
    }

    public void writeRawBuffer(byte[] val) {
        if (val != null && val.length > 0) {
            writeRawBuffer(val, 0, val.length);
        }
    }

    public void writeRawBuffer(byte[] val, int offset, int length) {
        if (val != null) {
            int amt = length < this.mChunkSize - this.mWriteIndex ? length : this.mChunkSize - this.mWriteIndex;
            if (amt > 0) {
                System.arraycopy(val, offset, this.mWriteBuffer, this.mWriteIndex, amt);
                this.mWriteIndex += amt;
                length -= amt;
                offset += amt;
            }
            while (length > 0) {
                nextWriteBuffer();
                amt = length < this.mChunkSize ? length : this.mChunkSize;
                System.arraycopy(val, offset, this.mWriteBuffer, this.mWriteIndex, amt);
                this.mWriteIndex += amt;
                length -= amt;
                offset += amt;
            }
        }
    }

    public void writeFromThisBuffer(int srcOffset, int size) {
        StringBuilder stringBuilder;
        if (this.mReadLimit < 0) {
            throw new IllegalStateException("writeFromThisBuffer before startEditing");
        } else if (srcOffset < getWritePos()) {
            stringBuilder = new StringBuilder();
            stringBuilder.append("Can only move forward in the buffer -- srcOffset=");
            stringBuilder.append(srcOffset);
            stringBuilder.append(" size=");
            stringBuilder.append(size);
            stringBuilder.append(WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER);
            stringBuilder.append(getDebugString());
            throw new IllegalArgumentException(stringBuilder.toString());
        } else if (srcOffset + size > this.mReadableSize) {
            stringBuilder = new StringBuilder();
            stringBuilder.append("Trying to move more data than there is -- srcOffset=");
            stringBuilder.append(srcOffset);
            stringBuilder.append(" size=");
            stringBuilder.append(size);
            stringBuilder.append(WifiEnterpriseConfig.CA_CERT_ALIAS_DELIMITER);
            stringBuilder.append(getDebugString());
            throw new IllegalArgumentException(stringBuilder.toString());
        } else if (size != 0) {
            if (srcOffset != (this.mWriteBufIndex * this.mChunkSize) + this.mWriteIndex) {
                int readBufIndex = srcOffset / this.mChunkSize;
                byte[] readBuffer = (byte[]) this.mBuffers.get(readBufIndex);
                int readIndex = srcOffset % this.mChunkSize;
                while (size > 0) {
                    if (this.mWriteIndex >= this.mChunkSize) {
                        nextWriteBuffer();
                    }
                    if (readIndex >= this.mChunkSize) {
                        readBufIndex++;
                        readBuffer = (byte[]) this.mBuffers.get(readBufIndex);
                        readIndex = 0;
                    }
                    int amt = Math.min(size, Math.min(this.mChunkSize - this.mWriteIndex, this.mChunkSize - readIndex));
                    System.arraycopy(readBuffer, readIndex, this.mWriteBuffer, this.mWriteIndex, amt);
                    this.mWriteIndex += amt;
                    readIndex += amt;
                    size -= amt;
                }
            } else if (size <= this.mChunkSize - this.mWriteIndex) {
                this.mWriteIndex += size;
            } else {
                size -= this.mChunkSize - this.mWriteIndex;
                this.mWriteIndex = size % this.mChunkSize;
                if (this.mWriteIndex == 0) {
                    this.mWriteIndex = this.mChunkSize;
                    this.mWriteBufIndex += size / this.mChunkSize;
                } else {
                    this.mWriteBufIndex += 1 + (size / this.mChunkSize);
                }
                this.mWriteBuffer = (byte[]) this.mBuffers.get(this.mWriteBufIndex);
            }
        }
    }

    public int getWritePos() {
        return (this.mWriteBufIndex * this.mChunkSize) + this.mWriteIndex;
    }

    public void rewindWriteTo(int writePos) {
        if (writePos <= getWritePos()) {
            this.mWriteBufIndex = writePos / this.mChunkSize;
            this.mWriteIndex = writePos % this.mChunkSize;
            if (this.mWriteIndex == 0 && this.mWriteBufIndex != 0) {
                this.mWriteIndex = this.mChunkSize;
                this.mWriteBufIndex--;
            }
            this.mWriteBuffer = (byte[]) this.mBuffers.get(this.mWriteBufIndex);
            return;
        }
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("rewindWriteTo only can go backwards");
        stringBuilder.append(writePos);
        throw new RuntimeException(stringBuilder.toString());
    }

    public int getRawFixed32At(int pos) {
        return (((((byte[]) this.mBuffers.get(pos / this.mChunkSize))[pos % this.mChunkSize] & 255) | ((((byte[]) this.mBuffers.get((pos + 1) / this.mChunkSize))[(pos + 1) % this.mChunkSize] & 255) << 8)) | ((((byte[]) this.mBuffers.get((pos + 2) / this.mChunkSize))[(pos + 2) % this.mChunkSize] & 255) << 16)) | ((255 & ((byte[]) this.mBuffers.get((pos + 3) / this.mChunkSize))[(pos + 3) % this.mChunkSize]) << 24);
    }

    public void editRawFixed32(int pos, int val) {
        ((byte[]) this.mBuffers.get(pos / this.mChunkSize))[pos % this.mChunkSize] = (byte) val;
        ((byte[]) this.mBuffers.get((pos + 1) / this.mChunkSize))[(pos + 1) % this.mChunkSize] = (byte) (val >> 8);
        ((byte[]) this.mBuffers.get((pos + 2) / this.mChunkSize))[(pos + 2) % this.mChunkSize] = (byte) (val >> 16);
        ((byte[]) this.mBuffers.get((pos + 3) / this.mChunkSize))[(pos + 3) % this.mChunkSize] = (byte) (val >> 24);
    }

    private static int zigZag32(int val) {
        return (val << 1) ^ (val >> 31);
    }

    private static long zigZag64(long val) {
        return (val << 1) ^ (val >> 63);
    }

    public byte[] getBytes(int size) {
        byte[] result = new byte[size];
        int bufCount = size / this.mChunkSize;
        int writeIndex = 0;
        int bufIndex = 0;
        while (bufIndex < bufCount) {
            System.arraycopy((byte[]) this.mBuffers.get(bufIndex), 0, result, writeIndex, this.mChunkSize);
            writeIndex += this.mChunkSize;
            bufIndex++;
        }
        int lastSize = size - (this.mChunkSize * bufCount);
        if (lastSize > 0) {
            System.arraycopy((byte[]) this.mBuffers.get(bufIndex), 0, result, writeIndex, lastSize);
        }
        return result;
    }

    public int getChunkCount() {
        return this.mBuffers.size();
    }

    public int getWriteIndex() {
        return this.mWriteIndex;
    }

    public int getWriteBufIndex() {
        return this.mWriteBufIndex;
    }

    public String getDebugString() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("EncodedBuffer( mChunkSize=");
        stringBuilder.append(this.mChunkSize);
        stringBuilder.append(" mBuffers.size=");
        stringBuilder.append(this.mBuffers.size());
        stringBuilder.append(" mBufferCount=");
        stringBuilder.append(this.mBufferCount);
        stringBuilder.append(" mWriteIndex=");
        stringBuilder.append(this.mWriteIndex);
        stringBuilder.append(" mWriteBufIndex=");
        stringBuilder.append(this.mWriteBufIndex);
        stringBuilder.append(" mReadBufIndex=");
        stringBuilder.append(this.mReadBufIndex);
        stringBuilder.append(" mReadIndex=");
        stringBuilder.append(this.mReadIndex);
        stringBuilder.append(" mReadableSize=");
        stringBuilder.append(this.mReadableSize);
        stringBuilder.append(" mReadLimit=");
        stringBuilder.append(this.mReadLimit);
        stringBuilder.append(" )");
        return stringBuilder.toString();
    }

    public void dumpBuffers(String tag) {
        int N = this.mBuffers.size();
        int start = 0;
        for (int i = 0; i < N; i++) {
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("{");
            stringBuilder.append(i);
            stringBuilder.append("} ");
            start += dumpByteString(tag, stringBuilder.toString(), start, (byte[]) this.mBuffers.get(i));
        }
    }

    public static void dumpByteString(String tag, String prefix, byte[] buf) {
        dumpByteString(tag, prefix, 0, buf);
    }

    private static int dumpByteString(String tag, String prefix, int start, byte[] buf) {
        StringBuffer sb = new StringBuffer();
        int length = buf.length;
        for (int i = 0; i < length; i++) {
            if (i % 16 == 0) {
                if (i != 0) {
                    Log.d(tag, sb.toString());
                    sb = new StringBuffer();
                }
                sb.append(prefix);
                sb.append('[');
                sb.append(start + i);
                sb.append(']');
                sb.append(' ');
            } else {
                sb.append(' ');
            }
            byte b = buf[i];
            byte c = (byte) ((b >> 4) & 15);
            if (c < (byte) 10) {
                sb.append((char) (48 + c));
            } else {
                sb.append((char) (87 + c));
            }
            byte d = (byte) (b & 15);
            if (d < (byte) 10) {
                sb.append((char) (48 + d));
            } else {
                sb.append((char) (87 + d));
            }
        }
        Log.d(tag, sb.toString());
        return length;
    }
}
