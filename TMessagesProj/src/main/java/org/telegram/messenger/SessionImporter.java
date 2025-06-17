package org.telegram.messenger;

import android.util.Base64;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class SessionImporter {

    public static class TelethonSessionData {
        public final int dcId;
        public final byte[] authKey;

        private TelethonSessionData(int dcId, byte[] authKey) {
            this.dcId = dcId;
            this.authKey = authKey;
        }
    }

    public static TelethonSessionData parse(String sessionString) {
        try {
            if (sessionString == null || sessionString.isEmpty()) {
                return null;
            }
            if (sessionString.startsWith("1")) {
                sessionString = sessionString.substring(1);
            }

            byte[] decodedBytes = Base64.decode(sessionString, Base64.URL_SAFE);
            if (decodedBytes == null || decodedBytes.length < 257) {
                return null;
            }
            ByteBuffer buffer = ByteBuffer.wrap(decodedBytes).order(ByteOrder.LITTLE_ENDIAN);

            int dcId = buffer.get() & 0xff;

            buffer.position(buffer.limit() - 256);
            byte[] authKey = new byte[256];
            buffer.get(authKey);

            if (dcId == 0) {
                FileLog.e("SessionImporter: DC ID is zero.");
                return null;
            }

            return new TelethonSessionData(dcId, authKey);

        } catch (Exception e) {
            FileLog.e("SessionImporter: " + e);
            return null;
        }
    }
}
