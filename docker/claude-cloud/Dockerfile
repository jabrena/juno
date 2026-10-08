# The development environment Claude Code cloud sessions get for this repository, as an image:
# JDK 27, the ARM GCC toolchain, QEMU, ffmpeg and ImageMagick, installed by the same
# .claude/hooks/install-toolchain.sh the cloud SessionStart hook runs, so the two never drift.
#
# Cloud sessions do not run this file: they run .claude/hooks/session-start.sh on their own base
# image. Use it to reproduce that environment anywhere Docker runs. From the repository root:
#
#   docker build -f docker/claude-cloud/Dockerfile -t juno-dev .
#   docker run --rm -it -v "$PWD":/workspace/juno juno-dev ./mvnw verify
FROM ubuntu:24.04

COPY .claude/hooks/install-toolchain.sh /usr/local/bin/install-toolchain.sh
RUN chmod +x /usr/local/bin/install-toolchain.sh \
    && /usr/local/bin/install-toolchain.sh \
    && rm -rf /var/lib/apt/lists/*

ENV JAVA_HOME=/opt/jdk-27
ENV PATH=/opt/jdk-27/bin:$PATH

WORKDIR /workspace/juno
CMD ["bash"]
