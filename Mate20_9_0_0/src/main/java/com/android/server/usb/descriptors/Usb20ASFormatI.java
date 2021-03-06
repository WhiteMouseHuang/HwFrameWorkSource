package com.android.server.usb.descriptors;

import com.android.server.usb.descriptors.report.ReportCanvas;

public final class Usb20ASFormatI extends UsbASFormat {
    private static final String TAG = "Usb20ASFormatI";
    private byte mBitResolution;
    private byte mSubSlotSize;

    public Usb20ASFormatI(int length, byte type, byte subtype, byte formatType, int subclass) {
        super(length, type, subtype, formatType, subclass);
    }

    public byte getSubSlotSize() {
        return this.mSubSlotSize;
    }

    public byte getBitResolution() {
        return this.mBitResolution;
    }

    public int parseRawDescriptors(ByteStream stream) {
        this.mSubSlotSize = stream.getByte();
        this.mBitResolution = stream.getByte();
        return this.mLength;
    }

    public void report(ReportCanvas canvas) {
        super.report(canvas);
        canvas.openList();
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Subslot Size: ");
        stringBuilder.append(getSubSlotSize());
        canvas.writeListItem(stringBuilder.toString());
        stringBuilder = new StringBuilder();
        stringBuilder.append("Bit Resolution: ");
        stringBuilder.append(getBitResolution());
        canvas.writeListItem(stringBuilder.toString());
        canvas.closeList();
    }
}
