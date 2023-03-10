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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.util.SVNLogType;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public abstract class SVNAbstractTunnelConnector implements ISVNConnector {

    private OutputStream myOutputStream;
    private InputStream myInputStream;
    private Process myProcess;
    private StreamLogger stderrConsumer;

    public void open(SVNRepositoryImpl repository, String process) throws SVNException {
        try {
            myProcess = Runtime.getRuntime().exec(process);
            myInputStream = new BufferedInputStream(myProcess.getInputStream());
            myOutputStream = new BufferedOutputStream(myProcess.getOutputStream());
            stderrConsumer = StreamLogger.consume(myProcess.getErrorStream());
        } catch (IOException e) {
            try {
                close(repository);
            } catch (SVNException inner) {
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.EXTERNAL_PROGRAM, "Cannot create tunnel: ''{0}''", e.getMessage());
            SVNErrorManager.error(err, e, SVNLogType.NETWORK);
        }
    }

    public void open(SVNRepositoryImpl repository, String[] command) throws SVNException {
        try {
            myProcess = Runtime.getRuntime().exec(command);
            myInputStream = new BufferedInputStream(myProcess.getInputStream());
            myOutputStream = new BufferedOutputStream(myProcess.getOutputStream());
            stderrConsumer = StreamLogger.consume(myProcess.getErrorStream());
        } catch (IOException e) {
            try {
                close(repository);
            } catch (SVNException inner) {
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.EXTERNAL_PROGRAM, "Cannot create tunnel: ''{0}''", e.getMessage());
            SVNErrorManager.error(err, e, SVNLogType.NETWORK);
        }
    }

    public InputStream getInputStream() throws IOException {
        return myInputStream;
    }

    public OutputStream getOutputStream() throws IOException {
        return myOutputStream;
    }

    public boolean isConnected(SVNRepositoryImpl repos) throws SVNException {
        return myInputStream != null;
    }

    public boolean isStale() {
        return false;
    }

    public void close(SVNRepositoryImpl repository) throws SVNException {
        if (stderrConsumer != null) {
            stderrConsumer.close();
            stderrConsumer = null;
        }
        if (myProcess != null) {
            if (myInputStream != null) {
                repository.getDebugLog().flushStream(myInputStream);
                SVNFileUtil.closeFile(myInputStream);
            }
            if (myOutputStream != null) {
                repository.getDebugLog().flushStream(myOutputStream);
                SVNFileUtil.closeFile(myOutputStream);
            }
            myProcess.destroy();
            myInputStream = null;
            myOutputStream = null;
            myProcess = null;
        }
    }

    public void free() {
    }

    public boolean occupy() {
        return true;
    }

}
