package com.test.session.servlet.wrappers;

import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;

import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

import com.test.session.api.ResponseWithSessionId;

/**
 * Allows ensuring that the session is propagated if the response is committed.
 * Each of the methods that can commit response will invoke
 * {@link #propagateSession()} method to insure that session id has been
 * propagated.
 */
public class HttpResponseWrapper extends HttpServletResponseWrapper implements ResponseWithSessionId {
    private static final int LN_LENGTH = System.getProperty("line.separator").length();
    private static final byte[] CRLF = new byte[]{'\r', '\n'};

    private final HttpRequestWrapper request;
    private final boolean delegatePrintWriter;

    private PrintWriter writer;
    private long contentWritten;
    protected long contentLength;
    private SaveSessionServletOutputStream outputStream;

    public HttpResponseWrapper(HttpRequestWrapper request, HttpServletResponse response, boolean delegatePrintWriter) {
        super(response);

        this.delegatePrintWriter = delegatePrintWriter;
        this.request = request;
    }

    @Override
    public final void sendError(int sc) throws IOException {
        request.propagateSession();
        super.sendError(sc);
    }

    @Override
    public final void sendError(int sc, String msg) throws IOException {
        request.propagateSession();
        super.sendError(sc, msg);
        closeOutput();
    }

    @Override
    public final void sendRedirect(String location) throws IOException {
        request.propagateSession();
        super.sendRedirect(location);
        closeOutput();
    }

    @Override
    public SaveSessionServletOutputStream getOutputStream() throws IOException {
        if (writer != null) {
            throw new IllegalStateException("Only one of getWriter()/getOutputStream() can be called, and writer is already used.");
        }

        if (outputStream == null) {
            outputStream = wrapOutputStream(super.getOutputStream());
        }

        return outputStream;
    }

    @Override
    public PrintWriter getWriter() throws IOException {
        if (outputStream != null) {
            throw new IllegalStateException("Only one of getWriter()/getOutputStream() can be called, and output stream is already used.");
        }

        if (writer == null) {
            writer = wrapPrintWriter();
        }

        return writer;
    }

    @Override
    public void addHeader(String name, String value) {
        checkContentLenghtHeader(name, value);
        super.addHeader(name, value);
    }

    

    @Override
    public void setHeader(String name, String value) {
        checkContentLenghtHeader(name, value);
        super.setHeader(name, value);
    }

    @Override
    public void setContentLength(int len) {
        contentLength = len;
        super.setContentLength(len);
    }

    @Override
    public void reset() {
        super.reset();
    }

    @Override
    public String encodeURL(String url) {
        try {
            return request.encodeURL(url);
        } catch (Exception ex) {
            return url;
        }
    }

    @Override
    public void flushBuffer() throws IOException {
        // On flush, we propagate session, then flush all buffers.
        flushAndPropagate();
        super.flushBuffer();
    }

    // @Override
    // public void setContentLengthLong(long len) {
    // contentLength = len;
    // super.setContentLengthLong(len);
    // }

    private void flushAndPropagate() throws IOException {
        if (outputStream != null) {
            outputStream.flush();
        } else if (writer != null) {
            writer.flush();
        } else {
            request.propagateSession();
        }
    }

    private void closeOutput() throws IOException {
        if (writer != null) {
            writer.close();
        }
        if (outputStream != null) {
            outputStream.close();
        }
    }

    private void checkContentLength(int contentLengthToWrite) {
        this.contentWritten += contentLengthToWrite;
        boolean isBodyFullyWritten = this.contentLength > 0 && this.contentWritten >= this.contentLength;
        int bufferSize = getBufferSize();
        boolean requiresFlush = bufferSize > 0 && this.contentWritten >= bufferSize;
        if (isBodyFullyWritten || requiresFlush) {
            try {
                flushAndPropagate();
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }
    }

    private void checkContentLenghtHeader(String name, String value) {
        // If added header is Content-Length, we need to use its value
        // as new contentLength.
        if ("content-length".equalsIgnoreCase(name)) {
            contentLength = Long.parseLong(value);
        }
    }

    private SaveSessionServletOutputStream wrapOutputStream(ServletOutputStream servletOutputStream) {
        return new SaveSessionServletOutputStream(servletOutputStream);
    }

    private PrintWriter wrapPrintWriter() throws IOException {
        String encoding = getCharacterEncoding();

        if (encoding == null) {
            // Using default coding as per Servlet standard
            encoding = "ISO-8859-1";
            setCharacterEncoding(encoding);
        }

        if (delegatePrintWriter) {
            return new DelegateServletPrintWriter(super.getWriter());
        }

        SaveSessionServletOutputStream wrappedStream = wrapOutputStream(super.getOutputStream());
        OutputStreamWriter osw = new OutputStreamWriter(wrappedStream, encoding);
        SimplestServletPrintWriter myWriter = new SimplestServletPrintWriter(osw);
        wrappedStream.setAssociated(myWriter);

        return myWriter;
    }

    /**
     * Ensures that session is indeed committed when calling methods that commit
     * the response. We delegate all methods to the original
     * {@link javax.servlet.ServletOutputStream} to ensure that the behavior is
     * as close as possible to the original one. To check if session needs to be
     * committed, we are counting number of bytes written out.
     *
     * Based on Spring Session code.
     */
    private class SaveSessionServletOutputStream extends ServletOutputStream {
        private final ServletOutputStream delegate;
        private Closeable associated;
        private boolean closing;

        private SaveSessionServletOutputStream(ServletOutputStream delegate) {
            this.delegate = delegate;
        }

        private void setAssociated(Closeable associated) {
            this.associated = associated;
        }

        @Override
        public void write(int b) throws IOException {
            checkContentLength(1);
            delegate.write(b);
        }

        @Override
        public void flush() throws IOException {
            request.propagateSession();
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            if (closing) {
                return;
            }
            closing = true;
            if (associated != null) {
                associated.close();
            }
            request.propagateSession();
            delegate.flush();
            delegate.close();
        }

        @Override
        public int hashCode() {
            return delegate.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return delegate.equals(obj);
        }

        @Override
        public void print(char c) throws IOException {
            print(String.valueOf(c));
        }

        @Override
        public void print(int i) throws IOException {
            print(String.valueOf(i));
        }

        @Override
        public void print(long l) throws IOException {
            print(String.valueOf(l));
        }

        @Override
        public void print(float f) throws IOException {
            print(String.valueOf(f));
        }

        @Override
        public void print(double d) throws IOException {
            print(String.valueOf(d));
        }

        @Override
        public void println() throws IOException {
            write(CRLF);
        }

        @Override
        public void println(String s) throws IOException {
            print(s);
            println();
        }

        @Override
        public void println(boolean b) throws IOException {
            print(b);
            println();
        }

        @Override
        public void println(char c) throws IOException {
            print(c);
            println();
        }

        @Override
        public void println(int i) throws IOException {
            print(i);
            println();
        }

        @Override
        public void println(long l) throws IOException {
            print(l);
            println();
        }

        @Override
        public void println(float f) throws IOException {
            print(f);
            println();
        }

        @Override
        public void write(byte[] b) throws IOException {
            checkContentLength(b.length);
            delegate.write(b);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            checkContentLength(len);
            delegate.write(b, off, len);
        }

        @Override
        public String toString() {
            return getClass().getName() + "[delegate=" + delegate.toString() + "]";
        }

        /**
         * For servlet 3.1. We do nothing in this method as the library may run
         * in servlet 2.x or 3.0 container. See
         * {@link HttpResponseWrapper31.SaveSessionServlet31OutputStream} for
         * logic used in servlet 3.1 containers.
         */
        // @Override
        // public boolean isReady() {
        // // Only for Servlet 3.1
        // return true;
        // }
        //
        // @Override
        // public void setWriteListener(WriteListener writeListener) {
        // // Only for Servlet 3.1
        // }
    }

    /**
     * Wrapper for {@link PrintWriter} that manages re-entrace of close()
     * method.
     */
    private static class SimplestServletPrintWriter extends PrintWriter {
        boolean closing;

        private SimplestServletPrintWriter(Writer out) {
            super(out);
        }

        @Override
        public void close() {
            // If close method has already been called, we will not re-enter
            // close() method of the wrapped writer.
            if (!closing) {
                closing = true;
                super.close();
            }
        }
    }

    /**
     * Wrapper for {@link PrintWriter} that relies on underlying container's
     * PrintWriter implementation.
     */
    private class DelegateServletPrintWriter extends PrintWriter {
        private final PrintWriter delegate;

        private DelegateServletPrintWriter(PrintWriter delegate) {
            super(delegate);
            this.delegate = delegate;
        }

        @Override
        public void flush() {
            request.propagateSession();
            delegate.flush();
        }

        @Override
        public void close() {
            request.propagateSession();
            delegate.close();
        }

        public int hashCode() {
            return delegate.hashCode();
        }

        public boolean equals(Object obj) {
            return delegate.equals(obj);
        }

        public String toString() {
            return getClass().getName() + "[delegate=" + delegate.toString() + "]";
        }

        @Override
        public boolean checkError() {
            return delegate.checkError();
        }

        @Override
        public void write(int c) {
            String s = Character.toString((char) c);
            write(s);
        }

        @Override
        public void write(char[] buf, int off, int len) {
            checkContentLength(len);
            delegate.write(buf, off, len);
        }

        @Override
        public void write(char[] buf) {
            checkContentLength(buf.length);
            delegate.write(buf);
        }

        @Override
        public void write(String s, int off, int len) {
            checkContentLength(len);
            delegate.write(s, off, len);
        }

        @Override
        public void write(final String s) {
            write(s, 0, s.length());
        }

        @Override
        public void print(final boolean b) {
            write(Boolean.toString(b));
        }

        @Override
        public void print(final char c) {
            write(Character.toString(c));
        }

        @Override
        public void print(final int i) {
            write(Integer.toString(i));
        }

        @Override
        public void print(final long l) {
            write(Long.toString(l));
        }

        @Override
        public void print(final float f) {
            write(Float.toString(f));
        }

        @Override
        public void print(final double d) {
            write(Double.toString(d));
        }

        @Override
        public void print(final char[] s) {
            write(s);
        }

        @Override
        public void print(final String s) {
            write(s == null ? "null" : s);
        }

        @Override
        public void print(final Object obj) {
            write(obj == null ? "null" : obj.toString());
        }

        @Override
        public void println() {
            checkContentLength(LN_LENGTH);
            super.println();
        }
    }
}
