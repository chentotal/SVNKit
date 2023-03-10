/*
 * ====================================================================
 * Copyright (c) 2004-2022 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.core.internal.io.svn;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.sshd.common.channel.exception.SshChannelClosedException;

/**
 * Copies an input stream to a logger
 */
public class StreamLogger implements Closeable {
    private static final Logger log = Logger.getLogger(StreamLogger.class.getName());
    private int copied;
    private Thread thread;

    public StreamLogger(String name, InputStream in, Logger logger, Level level) {
        thread = new Thread(()-> {
            try {
                ByteArrayOutputStream buffy = null;
                if (logger.isLoggable(level)) {
                    buffy = new ByteArrayOutputStream();
                }
                byte[] buf = new byte[2048];
                int length;
                int emptyRead = 0;
                while (!thread.isInterrupted() || emptyRead < 10) {
                    if (buffy != null) {
                        buffy.reset();
                    }
                    while (in.available() > 0 && (length = in.read(buf)) > 0) {
                        if (buffy != null) {
                            buffy.write(buf, 0, length);
                        }
                        copied += length;
                        emptyRead = 0;
                    }
                    emptyRead++;
                    if (buffy != null) {
                        if (logger.isLoggable(level)) {
                            logger.log(level, "Discarded input from " + name + ": " + buffy);
                        }
                    }
                    Thread.sleep(100);
                }
            } catch (SshChannelClosedException e) {
                // Nothing to do but quit
                logger.log(level, name+ ": Channel closed "+e);
            } catch (IOException e) {
                logger.log(level, name+ ": Failed while streaming "+e);
            } catch (InterruptedException e) {
                logger.log(level, name+ ": Got interrupted");
            } finally {
                try {
                    in.close();
                } catch (Exception e) {
                    logger.log(Level.FINE, "Got exception while closing the input stream", e);
                }
            }
        });
        thread.setName("Piping "+name);
        thread.start();
    }

    public static StreamLogger consume(InputStream errorStream) {
        return new StreamLogger("consumer",errorStream, log, Level.FINE);
    }

    @Override
    public void close() {
        if (thread != null) {
            if (thread.isAlive()) {
                thread.interrupt();
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    // Meh.
                }
            }
            thread = null;
        }
    }

    public int getCopied() {
        return copied;
    }
}
