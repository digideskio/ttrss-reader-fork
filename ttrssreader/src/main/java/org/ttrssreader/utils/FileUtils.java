/*
 * Copyright (c) 2015, Nils Braden
 *
 * This file is part of ttrss-reader-fork. This program is free software; you
 * can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation;
 * either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details. You should have received a
 * copy of the GNU General Public License along with this program; If
 * not, see http://www.gnu.org/licenses/.
 */

package org.ttrssreader.utils;

import org.ttrssreader.controllers.Controller;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Nils Braden
 */
public class FileUtils {

	private static final String TAG = FileUtils.class.getSimpleName();

	/**
	 * Supported extensions of imagefiles, see http://developer.android.com/guide/appendix/media-formats.html
	 */
	public static final String[] IMAGE_EXTENSIONS = {"jpeg", "jpg", "gif", "png", "bmp", "webp"};
	public static final String IMAGE_MIME = "image/*";

	/**
	 * Supported extensions of audiofiles, see http://developer.android.com/guide/appendix/media-formats.html
	 * I removed the extensions from this list which are also used for video files. It is easier to open these in the
	 * videoplayer and blaming the source instead of trying to figure out mime-types by hand.
	 */
	public static final String[] AUDIO_EXTENSIONS = {"mp3", "mid", "midi", "xmf", "mxmf", "ogg", "wav"};
	public static final String AUDIO_MIME = "audio/*";

	/**
	 * Supported extensions of videofiles, see http://developer.android.com/guide/appendix/media-formats.html
	 */
	public static final String[] VIDEO_EXTENSIONS = {"3gp", "mp4", "m4a", "aac", "ts", "webm", "mkv", "mpg", "mpeg",
			"avi", "flv"};
	public static final String VIDEO_MIME = "video/*";

	/**
	 * Path on sdcard to store files (DB, Certificates, ...)
	 */
	public static final String SDCARD_PATH_FILES = "/Android/data/org.ttrssreader/files/";

	/**
	 * Path on sdcard to store cache
	 */
	public static final String SDCARD_PATH_CACHE = "/Android/data/org.ttrssreader/cache/";

	/**
	 * Downloads a given URL directly to a file, when maxSize bytes are reached the download is stopped and the file is
	 * deleted.
	 *
	 * @param downloadUrl the URL of the file
	 * @param file        the destination file
	 * @param maxSize     the size in bytes after which to abort the download
	 * @param minSize     the minimum size in bytes after which to start the download
	 * @return length of downloaded file or negated file length if it exceeds {@code maxSize} or downloaded with
	 * errors.
	 * So, if returned value less or equals to 0, then the file was not cached.
	 */
	public static long downloadToFile(String downloadUrl, File file, long maxSize, long minSize) {
		if (file.exists() && file.length() > 0) return file.length();

		long byteWritten = 0;
		boolean error = false;
		InputStream is = null;

		try (FileOutputStream fos = new FileOutputStream(file)) {
			// In case where http and https is supported we might get just // as URL prefix, so we use a secure default...
			if(downloadUrl.startsWith("//"))
				downloadUrl = "https:" + downloadUrl;

			URL url = new URL(downloadUrl);
			URLConnection connection = Controller.getInstance().openConnection(url);

			connection.setConnectTimeout((int) (Utils.SECOND * 2));
			connection.setReadTimeout((int) Utils.SECOND);

			// Check filesize if available from header
			try {
				long length = Long.parseLong(connection.getHeaderField("Content-Length"));

				if (length <= 0) {
					byteWritten = length;
					Log.w(TAG, "Content-Length equals 0 or is negative: " + length);
				} else if (length < minSize) {
					error = true;
					byteWritten = -length;
					Log.i(TAG, String.format(
							"Not starting download of %s, the size (%s bytes) is less then the minimum "
									+ "filesize of %s bytes.", downloadUrl, length, minSize));
				} else if (length > maxSize) {
					error = true;
					byteWritten = -length;
					Log.i(TAG, String.format(
							"Not starting download of %s, the size (%s bytes) exceeds the " + "maximum "
									+ "filesize of %s bytes.", downloadUrl, length, maxSize));
				}
			} catch (Exception e) {
				Log.w(TAG, "Couldn't read Content-Length from url: " + downloadUrl);
			}

			if (byteWritten == 0) {
				if (!file.exists() && !file.createNewFile())
					Log.i(TAG, "File could not be created: " + file.getAbsolutePath());

				is = connection.getInputStream();

				int size = (int) Utils.KB * 8;
				byte[] buf = new byte[size];
				int byteRead;

				while (((byteRead = is.read(buf)) != -1)) {
					fos.write(buf, 0, byteRead);
					byteWritten += byteRead;

					if (byteWritten > maxSize) {
						Log.w(TAG, String.format(
								"Download interrupted, the size of %s bytes exceeds maximum " + "filesize.",
								byteWritten));
						// file length should be negated if file size exceeds {@code maxSize}
						error = true;
						byteWritten = -byteWritten;
						break;
					}
				}
			}
		} catch (Exception e) {
			Log.e(TAG, "Download not finished properly. Exception: " + e.getMessage(), e);
			error = true;
			byteWritten = -file.length();
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException ignored) {
				}
			}
		}

		if (error)
			Log.w(TAG, String.format("Stopped download from url '%s'. Downloaded %d bytes", downloadUrl, byteWritten));
		else Log.i(TAG, String.format("Download from '%s' finished. Downloaded %d bytes", downloadUrl, byteWritten));

		if (error && file.exists())
			if (!file.delete()) Log.w(TAG, "File could not be deleted: " + file.getAbsolutePath());

		return byteWritten;
	}

	/**
	 * At the moment this method just returns a generic mime-type for audio, video or image-files, a more specific way
	 * of probing for the type (MIME-Sniffing or exact checks on the extension) are yet to be implemented.
	 *
	 * Implementation-Hint: See
	 * https://code.google.com/p/openintents/source/browse/trunk/filemanager/FileManager/src/org
	 * /openintents/filemanager/FileManagerActivity.java
	 */
	public static String getMimeType(String fileName) {
		String ret = "";
		if (fileName == null || fileName.length() == 0) return ret;

		for (String ext : IMAGE_EXTENSIONS) {
			if (fileName.endsWith(ext)) return IMAGE_MIME;
		}
		for (String ext : AUDIO_EXTENSIONS) {
			if (fileName.endsWith(ext)) return AUDIO_MIME;
		}
		for (String ext : VIDEO_EXTENSIONS) {
			if (fileName.endsWith(ext)) return VIDEO_MIME;
		}

		return ret;
	}

	/**
	 * group given files (URLs) into hash by mime-type
	 *
	 * @param attachments collection of file names (URLs)
	 * @return map, which keys are found mime-types and values are file collections of this mime-type
	 */
	public static Map<String, Collection<String>> groupFilesByMimeType(Collection<String> attachments) {
		Map<String, Collection<String>> attachmentsByMimeType = new HashMap<>();
		for (String url : attachments) {
			String mimeType = getMimeType(url);

			if (mimeType.length() > 0) {
				Collection<String> mimeTypeList = attachmentsByMimeType.get(mimeType);

				if (mimeTypeList == null) {
					mimeTypeList = new ArrayList<>();
					attachmentsByMimeType.put(mimeType, mimeTypeList);
				}

				mimeTypeList.add(url);
			}
		}

		return attachmentsByMimeType;
	}

	/**
	 * From http://stackoverflow.com/a/4943771
	 *
	 * @return false if delete() failed on any of the files
	 */
	public static boolean deleteFolderRcursive(final File dir) {
		boolean ret = true;
		if (dir.isDirectory()) {
			String[] children = dir.list();
			for (String aChildren : children) {
				ret &= new File(dir, aChildren).delete();
			}
		}
		return ret;
	}

}
