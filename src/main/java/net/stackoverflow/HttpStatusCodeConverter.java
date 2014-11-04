package net.stackoverflow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;

public class HttpStatusCodeConverter implements Filter {

    private static final Logger logger
            = Logger.getLogger(HttpStatusCodeConverter.class.getName());
    private Map<Integer, Integer> errorMap;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain)
            throws IOException, ServletException {

        if (response instanceof HttpServletResponse) {
            AntiCommitResponseWrapper hr = new AntiCommitResponseWrapper(
                    (HttpServletResponse) response);

            //pre-filter check for error codes, if code exists, no need to continue
            if (!hasFilterCode(hr)) {
                //no error yet, progress through filters
                try {
                    chain.doFilter(request, hr);
                } catch (Throwable t) {
                    if (!hasFilterCode(hr)) {
                        //exception from a filter but no code was set, set it now
                        hr.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    }
                }
            }

            //do post-filter check for error codes
            if (hasFilterCode(hr)) {
                hr.sendError(errorMap.get(hr.getStatus()));
            }

            hr.complete();
        }

    }

    private boolean hasFilterCode(HttpServletResponse hr) {
        return errorMap != null && errorMap.containsKey(hr.getStatus());
    }

    @Override
    public void destroy() {
    }

    @Override
    public void init(FilterConfig filterConfig) {
        errorMap = new HashMap<>();
        Enumeration<String> paramNames = filterConfig.getInitParameterNames();
        while (paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            try {
                errorMap.put(Integer.valueOf(name),
                        Integer.valueOf(filterConfig.getInitParameter(name)));
            } catch (NumberFormatException ex) {
                logger.log(Level.WARNING, "Invalid HTTP status code mapping "
                        + "''{0}''->''{1}''.",
                        new Object[]{name, filterConfig.getInitParameter(name)});
            }
        }
    }

    private class AntiCommitResponseWrapper extends HttpServletResponseWrapper {

        private int status = SC_OK;
        private String statusMsg;
        private String redirectLocation;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        public AntiCommitResponseWrapper(HttpServletResponse response) {
            super(response);
        }

        @Override
        public int getStatus() {
            return status;
        }

        @Override
        public void setStatus(int sc) {
            this.status = sc;
        }

        @Override
        public void sendRedirect(String location) throws IOException {
            this.status = SC_FOUND;
            this.redirectLocation = location;
        }

        @Override
        public void sendError(int sc) throws IOException {
            this.status = sc;
        }

        @Override
        public void sendError(int sc, String msg) throws IOException {
            this.status = sc;
            this.statusMsg = msg;
        }

        @Override
        public void resetBuffer() {
            buffer.reset();
        }

        @Override
        public void reset() {
            buffer.reset();
            status = SC_OK;
            statusMsg = null;
            super.reset();
        }

        @Override
        public void flushBuffer() throws IOException {
        }

        @Override
        public void setBufferSize(int size) {
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            return new ServletOutputStream() {

                @Override
                public void write(int i) throws IOException {
                    buffer.write(i);
                }
            };
        }

        /**
         * Send everything to the client.
         */
        private void complete() throws IOException {
            if (status != SC_OK) {
                super.sendError(status, statusMsg);
            } else if (status == SC_FOUND) {
                super.sendRedirect(redirectLocation);
            } else {
                super.setStatus(status);
                try (OutputStream out = super.getOutputStream()) {
                    out.write(buffer.toByteArray());
                }
            }

        }
    }
}
