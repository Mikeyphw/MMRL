package com.dergoogler.mmrl.platform.stub;

import android.os.ParcelFileDescriptor;

interface IFileManager {
    boolean deleteOnExit(String path);
    String[] list(String path);
    long stat(String path);
    long length(String path);
    boolean delete(String path);
    boolean exists(String path);
    boolean isDirectory(String path);
    boolean isFile(String path);
    boolean isBlock(String path);
    boolean isCharacter(String path);
    boolean isSymlink(String path);
    boolean isNamedPipe(String path);
    boolean isSocket(String path);
    boolean mkdir(String path);
    boolean mkdirs(String path);
    boolean createNewFile(String path);
    boolean renameTo(String target, String dest);
    void copyTo(String path, String target, boolean overwrite);
    boolean canExecute(String path);
    boolean canWrite(String path);
    boolean canRead(String path);
    boolean isHidden(String path);
    boolean setPermissions(String path, int mode);
    boolean setOwner(String path, int owner, int group);
    ParcelFileDescriptor parcelFile(String path);
    ParcelFileDescriptor openFile(String path, int flags, int mode);
    int getMode(String path);
}
