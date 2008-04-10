/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli2.svnlook;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.tmatesoft.svn.cli2.AbstractSVNCommand;
import org.tmatesoft.svn.cli2.AbstractSVNCommandEnvironment;
import org.tmatesoft.svn.cli2.AbstractSVNOption;
import org.tmatesoft.svn.cli2.SVNCommandLine;
import org.tmatesoft.svn.cli2.SVNOptionValue;
import org.tmatesoft.svn.cli2.svn.SVNCommand;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.fs.FSRepository;
import org.tmatesoft.svn.core.internal.io.fs.FSTransactionInfo;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.internal.wc.SVNPath;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.ISVNOptions;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public class SVNLookCommandEnvironment extends AbstractSVNCommandEnvironment {

    private long myRevision;
    private String myTransaction;
    private boolean myIsNonRecursive;
    private boolean myIsVerbose;
    private boolean myIsHelp;
    private boolean myIsRevProp;
    private boolean myIsVersion;
    private boolean myIsShowIDs;
    private long myLimit;
    private boolean myIsNoDiffDeleted;
    private boolean myIsNoDiffAdded;
    private boolean myIsDiffCopyFrom;
    private boolean myIsFullPaths;
    private boolean myIsCopyInfo;
    private String myExtension;
    private boolean myIsRevision;
    private File myRepositoryFile;
    private FSRepository myRepository;
    private FSTransactionInfo myTransactionInfo;
    private String myArgument1;
    private String myArgument2;

    public SVNLookCommandEnvironment(String programName, PrintStream out, PrintStream err, InputStream in) {
        super(programName, out, err, in);
        myRevision = -1;
    }
    
    public File getRepositoryFile() {
        return myRepositoryFile;
    }
    
    public long getRevision() {
        return myRevision;
    }
    
    public String getTransaction() {
        return myTransaction;
    }
    
    public boolean isNonRecursive() {
        return myIsNonRecursive;
    }
    
    public boolean isVerbose() {
        return myIsVerbose;
    }
    
    public boolean isHelp() {
        return myIsHelp;
    }
    
    public boolean isRevProp() {
        return myIsRevProp;
    }
    
    public boolean isVersion() {
        return myIsVersion;
    }
    
    public boolean isShowIDs() {
        return myIsShowIDs;
    }
    
    public long getLimit() {
        return myLimit;
    }
    
    public boolean isNoDiffDeleted() {
        return myIsNoDiffDeleted;
    }
    
    public boolean isNoDiffAdded() {
        return myIsNoDiffAdded;
    }

    public boolean isDiffCopyFrom() {
        return myIsDiffCopyFrom;
    }

    public boolean isFullPaths() {
        return myIsFullPaths;
    }
    
    public boolean isCopyInfo() {
        return myIsCopyInfo;
    }
    
    public String getExtension() {
        return myExtension;
    }
    
    public boolean isRevision() {
        return myIsRevision;
    }
    
    public FSTransactionInfo getTransactionInfo() {
        return myTransactionInfo;
    }
    
    public FSRepository getRepository() {
        return myRepository;
    }
    
    public String getFirstArgument() {
        return myArgument1;
    }

    public String getSecondArgument() {
        return myArgument2;
    }

    protected ISVNAuthenticationManager createClientAuthenticationManager() {
        return SVNWCUtil.createDefaultAuthenticationManager();
    }

    protected ISVNOptions createClientOptions() {
        return SVNWCUtil.createDefaultOptions(true);
    }

    protected void validateOptions(SVNCommandLine commandLine) throws SVNException {
        super.validateOptions(commandLine);
        if (myRevision >= 0 && myTransaction != null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_MUTUALLY_EXCLUSIVE_ARGS, 
                    "The '--transaction' (-t) and '--revision' (-r) arguments can not co-exist");
            SVNErrorManager.error(err);                    
        }
        myIsRevision = myTransaction == null;
        
        if (!(myIsHelp || myIsVersion || "help".equals(commandLine.getCommandName()))) {
            if (getArguments().isEmpty()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Repository argument required");
                SVNErrorManager.error(err);
            }
            SVNPath path = new SVNPath((String) getArguments().get(0), false);
            if (path.isURL()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "''{0}'' is URL when it should be a path", path.getTarget());
                SVNErrorManager.error(err);
            }
            myRepositoryFile = path.getFile();
            myRepository = (FSRepository) SVNRepositoryFactory.create(SVNURL.fromFile(myRepositoryFile));
            myRepository.setCanceller(this);
            myRepository.testConnection();
            if (getTransaction() != null) {
                myTransactionInfo = myRepository.getFSFS().openTxn(getTransaction());
            } else {
                if (myRevision < 0) {
                    myRevision = myRepository.getLatestRevision();
                }
            }
            List updatedArguments = new LinkedList(getArguments());
            updatedArguments.remove(0);
            if (!updatedArguments.isEmpty()) {
                myArgument1 = (String) updatedArguments.remove(0);
            }
            if (!updatedArguments.isEmpty()) {
                myArgument2 = (String) updatedArguments.remove(0);
            }
            setArguments(updatedArguments);
        }
    }

    protected void initOption(SVNOptionValue optionValue) throws SVNException {
        AbstractSVNOption option = optionValue.getOption();
        if (option == SVNLookOption.REVISION) {
            long revision = -1; 
            if (optionValue.getValue() != null) {
                try {
                    revision = Long.parseLong(optionValue.getValue());
                } catch (NumberFormatException nfe) {
                }
            }
            if (revision < 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Invalid revision number supplied");
                SVNErrorManager.error(err);
            }
            myRevision = revision;
        } else if (option == SVNLookOption.TRANSACTION) {
            myTransaction = optionValue.getValue();
        } else if (option == SVNLookOption.NON_RECURSIVE) {
            myIsNonRecursive = true;
        } else if (option == SVNLookOption.VERBOSE) {
            myIsVerbose = true;
        } else if (option == SVNLookOption.HELP || option == SVNLookOption.QUESTION) {
            myIsHelp = true;
        } else if (option == SVNLookOption.REVPROP) {
            myIsRevProp = true;
        } else if (option == SVNLookOption.VERSION) {
            myIsVersion = true;
        } else if (option == SVNLookOption.SHOW_IDS) {
            myIsShowIDs = true;
        } else if (option == SVNLookOption.LIMIT) {
            long limit = -1; 
            if (optionValue.getValue() != null) {
                try {
                    limit = Long.parseLong(optionValue.getValue());
                } catch (NumberFormatException nfe) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Non-numeric limit argument given");
                    SVNErrorManager.error(err);
                }
            } else {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Non-numeric limit argument given");
                SVNErrorManager.error(err);
            }
            if (limit <= 0) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_ARG_PARSING_ERROR, "Argument to --limit must be positive");
                SVNErrorManager.error(err);
            }
            myLimit = limit;
        } else if (option == SVNLookOption.NO_DIFF_DELETED) {
            myIsNoDiffDeleted = true;
        } else if (option == SVNLookOption.NO_DIFF_ADDED) {
            myIsNoDiffAdded = true;
        } else if (option == SVNLookOption.DIFF_COPY_FROM) {
            myIsDiffCopyFrom = true;
        } else if (option == SVNLookOption.FULL_PATHS) {
            myIsFullPaths = true;
        } else if (option == SVNLookOption.COPY_INFO) {
            myIsCopyInfo = true;
        } else if (option == SVNLookOption.EXTENSION) {
            myExtension = optionValue.getValue();
        }
     }

    protected String refineCommandName(String commandName) throws SVNException {
        if (myIsHelp) {
            List newArguments = commandName != null ? Collections.singletonList(commandName) : Collections.EMPTY_LIST;
            setArguments(newArguments);
            return "help";
        } 
        if (commandName == null) {
            if (myIsVersion) {
                SVNCommand versionCommand = new SVNCommand("--version", null) {
                    protected Collection createSupportedOptions() {
                        return Arrays.asList(new AbstractSVNOption[] {SVNLookOption.VERSION});
                    }
                    public void run() throws SVNException {
                        AbstractSVNCommand helpCommand = AbstractSVNCommand.getCommand("help");
                        helpCommand.init(SVNLookCommandEnvironment.this);
                        helpCommand.run();
                    }
                };
                AbstractSVNCommand.registerCommand(versionCommand);
                return "--version";
            }
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CL_INSUFFICIENT_ARGS, "Subcommand argument required");
            SVNErrorManager.error(err);
        }
        return commandName;
    }

}
