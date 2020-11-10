package com.composum.sling.clientlibs.processor;

import com.composum.sling.clientlibs.service.ClientlibService;
import com.composum.sling.core.util.ResourceUtil;
import org.apache.commons.io.IOUtils;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.zip.GZIPOutputStream;

@Component(
        property = {
                Constants.SERVICE_DESCRIPTION + "=Composum Nodes Clientlib Default Zip Processor"
        }
)
public class DefaultGzipProcessor implements GzipProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultGzipProcessor.class);

    @Override
    public InputStream processContent(final InputStream source, ProcessorContext context)
            throws IOException {
        InputStream result = source;
        if (source != null) {
            context.hint(ResourceUtil.PROP_ENCODING, ClientlibService.ENCODING_GZIP);
            final PipedOutputStream outputStream = new PipedOutputStream();
            result = new PipedInputStream(outputStream);
            final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
            context.execute(() -> {
                try {
                    IOUtils.copy(source, gzipOutputStream);
                    gzipOutputStream.flush();
                } catch (IOException ex) {
                    LOG.error(ex.getMessage(), ex);
                } finally {
                    IOUtils.closeQuietly(gzipOutputStream);
                }
            });
        }
        return result;
    }
}
