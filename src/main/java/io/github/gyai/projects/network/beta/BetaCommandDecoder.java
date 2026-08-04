package io.github.gyai.projects.network.beta;

@FunctionalInterface
public interface BetaCommandDecoder {
    DecodeResult decode(BetaCommandEnvelope envelope);

    record DecodeResult(BetaDecodedCommand command, String detail) {
        public DecodeResult {
            detail = detail == null ? "" : detail;
            if (detail.length() > 256) detail = detail.substring(0, 256);
        }

        public static DecodeResult success(BetaDecodedCommand command) {
            return new DecodeResult(java.util.Objects.requireNonNull(command), "");
        }

        public static DecodeResult failure(String detail) {
            return new DecodeResult(null, detail);
        }

        public boolean successful() {
            return command != null;
        }
    }
}
