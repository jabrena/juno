package io.github.jabrena.juno.api.thirdparty.lego.rcx;

import io.github.jabrena.juno.thirdparty.lego.rcx.RcxFirmware;

/** Allocation-free transfer bookkeeping after construction; only acknowledged blocks advance the offset. */
final class RcxDownloadProgress {
    private static final int FIRST_BLOCK_BYTES = 64;
    private static final int MIN_BLOCK_BYTES = 16;
    private static final int BLOCK_ATTEMPTS = 3;
    private static final int SESSION_ATTEMPTS = 5;

    private final int length;
    private int offset;
    private int sequence;
    private int failures;
    private int session;
    private int blockBytes = FIRST_BLOCK_BYTES;

    RcxDownloadProgress(int length) {
        this.length = length;
    }

    boolean restart() {
        if (session >= SESSION_ATTEMPTS) {
            return false;
        }
        if (session > 0) {
            blockBytes = Math.max(MIN_BLOCK_BYTES, blockBytes / 2);
        }
        session++;
        offset = 0;
        sequence = 1;
        failures = 0;
        return true;
    }

    int session() {
        return session;
    }

    int offset() {
        return offset;
    }

    int size() {
        return Math.min(blockBytes, length - offset);
    }

    int index() {
        return offset + size() == length ? 0 : sequence;
    }

    boolean complete() {
        return offset == length;
    }

    /** True means continue this session. A lost final acknowledgement or protocol error needs a fresh start. */
    boolean accept(int status) {
        if (status == 0) {
            offset += size();
            sequence++;
            failures = 0;
            return true;
        }
        failures++;
        return status == RcxFirmware.NO_REPLY && index() != 0 && failures < BLOCK_ATTEMPTS;
    }
}
