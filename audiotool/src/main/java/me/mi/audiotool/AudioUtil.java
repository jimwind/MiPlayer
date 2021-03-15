package me.mi.audiotool;

public class AudioUtil {
    /**
     * 分离双声道数据 取左声道
     *
     * @param data
     * @return
     */
    public static byte[] splitStereoPcm(byte[] data) {
        int monoLength = data.length / 2;
        byte[] leftData = new byte[monoLength];
        byte[] rightData = new byte[monoLength];
        for (int i = 0; i < monoLength; i++) {
            if (i % 2 == 0) {
                System.arraycopy(data, i * 2, leftData, i, 2);
            } else {
                System.arraycopy(data, i * 2, rightData, i - 1, 2);
            }
        }
        return leftData;
    }

    private static short getShort(byte[] data, int start) {
        return (short) ((data[start] & 0xFF) | (data[start + 1] << 8));
    }

    public static int amplifyPCMData(byte[] pData, int nLen, byte[] data2, int nBitsPerSample, float multiple) {
        int nCur = 0;
        if (16 == nBitsPerSample) {
            while (nCur < nLen) {
                short volum = getShort(pData, nCur);

                volum = (short) (volum * multiple);

                data2[nCur] = (byte) (volum & 0xFF);
                data2[nCur + 1] = (byte) ((volum >> 8) & 0xFF);
                nCur += 2;
            }

        }
        return 0;
    }

    public static float computeDB(double db) {
        return (float) Math.pow(10, db / 20);
    }
}
