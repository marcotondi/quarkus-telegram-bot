package io.quarkiverse.telegram.bot.runtime.model;

import java.nio.file.Path;

public sealed interface InputFile permits InputFile.FileId, InputFile.Url, InputFile.BinaryPath, InputFile.BinaryBytes {

    record FileId(String value) implements InputFile {
    }

    record Url(String value) implements InputFile {
    }

    record BinaryPath(Path path, String fileName, String contentType) implements InputFile {
    }

    record BinaryBytes(byte[] data, String fileName, String contentType) implements InputFile {
    }

    static InputFile fromFileId(String fileId) {
        return new FileId(fileId);
    }

    static InputFile fromUrl(String url) {
        return new Url(url);
    }

    static InputFile fromPath(Path path) {
        return new BinaryPath(path, path.getFileName().toString(), "application/octet-stream");
    }

    static InputFile fromBytes(byte[] data, String fileName, String contentType) {
        return new BinaryBytes(data, fileName, contentType);
    }
}
