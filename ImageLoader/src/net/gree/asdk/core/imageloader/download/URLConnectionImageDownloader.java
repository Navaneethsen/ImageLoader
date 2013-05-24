package net.gree.asdk.core.imageloader.download;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;

/**
 * Default implementation of ImageDownloader. Uses {@link URLConnection} for image stream retrieving.
 * 
 * @author Sergey Tarasevich (nostra13[at]gmail[dot]com)
 */
public class URLConnectionImageDownloader extends ImageDownloader {

	/** {@value} */
	public static final int DEFAULT_HTTP_CONNECT_TIMEOUT = 5 * 1000; // milliseconds
	/** {@value} */
	public static final int DEFAULT_HTTP_READ_TIMEOUT = 20 * 1000; // milliseconds

	private int connectTimeout;
	private int readTimeout;

	public URLConnectionImageDownloader() {
		this(DEFAULT_HTTP_CONNECT_TIMEOUT, DEFAULT_HTTP_READ_TIMEOUT);
	}

	public URLConnectionImageDownloader(int connectTimeout, int readTimeout) {
		this.connectTimeout = connectTimeout;
		this.readTimeout = readTimeout;
	}

	@Override
	public InputStream getStreamFromNetwork(URI imageUri) throws IOException {
		URLConnection conn = imageUri.toURL().openConnection();
		conn.setConnectTimeout(connectTimeout);
		conn.setReadTimeout(readTimeout);
		return new FlushedInputStream(new BufferedInputStream(conn.getInputStream(), BUFFER_SIZE));
	}
	
	/**
	 * Many streams obtained over slow connection show <a href="http://code.google.com/p/android/issues/detail?id=6066">this
	 * problem</a>.
	 */
	public static class FlushedInputStream extends FilterInputStream {

		public FlushedInputStream(InputStream inputStream) {
			super(inputStream);
		}

		@Override
		public long skip(long n) throws IOException {
			long totalBytesSkipped = 0L;
			while (totalBytesSkipped < n) {
				long bytesSkipped = in.skip(n - totalBytesSkipped);
				if (bytesSkipped == 0L) {
					int by_te = read();
					if (by_te < 0) {
						break; // we reached EOF
					} else {
						bytesSkipped = 1; // we read one byte
					}
				}
				totalBytesSkipped += bytesSkipped;
			}
			return totalBytesSkipped;
		}
	}
}