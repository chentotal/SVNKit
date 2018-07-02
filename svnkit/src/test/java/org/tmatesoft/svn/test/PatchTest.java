package org.tmatesoft.svn.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.wc.SVNFileType;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc.patch.SVNPatchTargetInfo;
import org.tmatesoft.svn.core.internal.wc2.ng.SvnDiffGenerator;
import org.tmatesoft.svn.core.internal.wc2.patch.ISvnPatchContext;
import org.tmatesoft.svn.core.internal.wc2.patch.SvnPatchFile;
import org.tmatesoft.svn.core.internal.wc2.patch.SvnPatchTarget;
import org.tmatesoft.svn.core.wc.ISVNEventHandler;
import org.tmatesoft.svn.core.wc.SVNEvent;
import org.tmatesoft.svn.core.wc.SVNEventAction;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnDiff;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnPatch;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class PatchTest {

    @Test
    public void testModification() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testModification", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", "old".getBytes());
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", "new".getBytes());
            commitBuilder2.commit();

            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(new File("").getAbsoluteFile());
            final ByteArrayOutputStream output = new ByteArrayOutputStream();

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSource(SvnTarget.fromURL(url), SVNRevision.create(1), SVNRevision.create(2));
            diff.setDiffGenerator(diffGenerator);
            diff.setOutput(output);
            diff.run();

            final String patchString = output.toString();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File file = workingCopy.getFile("file");
            final File patchFile = new File(sandbox.createDirectory("directory"), "patchFile");

            TestUtil.writeFileContentsString(patchFile, patchString);

            final UpdateTest.EventsHandler eventHandler = new UpdateTest.EventsHandler();
            svnOperationFactory.setEventHandler(eventHandler);

            final SvnPatch patch = svnOperationFactory.createPatch();
            patch.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            patch.setPatchFile(patchFile);
            patch.run();

            Assert.assertEquals("new", TestUtil.readFileContentsString(file));

            final List<SVNEvent> events = eventHandler.getEvents();
            for (SVNEvent event : events) {
                System.out.println("event = " + event);
            }
            Assert.assertEquals(2, events.size());
            Assert.assertEquals(SVNEventAction.PATCH, events.get(0).getAction());
            Assert.assertEquals(file, events.get(0).getFile());
            Assert.assertEquals(SVNEventAction.PATCH_APPLIED_HUNK, events.get(1).getAction());
            Assert.assertEquals(file, events.get(1).getFile());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testAddition() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testAddition", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", "new".getBytes());
            commitBuilder1.commit();

            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(new File("").getAbsoluteFile());
            final ByteArrayOutputStream output = new ByteArrayOutputStream();

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSource(SvnTarget.fromURL(url), SVNRevision.create(0), SVNRevision.create(1));
            diff.setDiffGenerator(diffGenerator);
            diff.setOutput(output);
            diff.run();

            final String patchString = output.toString();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 0);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File file = workingCopy.getFile("file");
            final File patchFile = new File(sandbox.createDirectory("directory"), "patchFile");

            TestUtil.writeFileContentsString(patchFile, patchString);

            final UpdateTest.EventsHandler eventHandler = new UpdateTest.EventsHandler();
            svnOperationFactory.setEventHandler(eventHandler);

            final SvnPatch patch = svnOperationFactory.createPatch();
            patch.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            patch.setPatchFile(patchFile);
            patch.run();

            Assert.assertEquals("new", TestUtil.readFileContentsString(file));

            final List<SVNEvent> events = eventHandler.getEvents();
            Assert.assertEquals(1, events.size());
            Assert.assertEquals(SVNEventAction.ADD, events.get(0).getAction());
            Assert.assertEquals(file, events.get(0).getFile());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDeletion() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDeletion", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", "new".getBytes());
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("file");
            commitBuilder2.commit();

            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(new File("").getAbsoluteFile());
            final ByteArrayOutputStream output = new ByteArrayOutputStream();

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSource(SvnTarget.fromURL(url), SVNRevision.create(1), SVNRevision.create(2));
            diff.setDiffGenerator(diffGenerator);
            diff.setOutput(output);
            diff.run();

            final String patchString = output.toString();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File file = workingCopy.getFile("file");
            final File patchFile = new File(sandbox.createDirectory("directory"), "patchFile");

            TestUtil.writeFileContentsString(patchFile, patchString);

            final UpdateTest.EventsHandler eventHandler = new UpdateTest.EventsHandler();
            svnOperationFactory.setEventHandler(eventHandler);

            final SvnPatch patch = svnOperationFactory.createPatch();
            patch.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            patch.setPatchFile(patchFile);
            patch.run();

            Assert.assertFalse(file.exists());

            final List<SVNEvent> events = eventHandler.getEvents();
            Assert.assertEquals(1, events.size());
            Assert.assertEquals(SVNEventAction.DELETE, events.get(0).getAction());
            Assert.assertEquals(file, events.get(0).getFile());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testMovement() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testMovement", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", "new".getBytes());
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.delete("file");
            commitBuilder2.addFileByCopying("movedFile", "file");
            commitBuilder2.commit();

            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(new File("").getAbsoluteFile());
            final ByteArrayOutputStream output = new ByteArrayOutputStream();

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSource(SvnTarget.fromURL(url), SVNRevision.create(1), SVNRevision.create(2));
            diff.setDiffGenerator(diffGenerator);
            diff.setOutput(output);
            diff.run();

            final String patchString = output.toString();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final File file = workingCopy.getFile("file");
            final File movedFile = workingCopy.getFile("movedFile");
            final File patchFile = new File(sandbox.createDirectory("directory"), "patchFile");

            TestUtil.writeFileContentsString(patchFile, patchString);

            final UpdateTest.EventsHandler eventHandler = new UpdateTest.EventsHandler();
            svnOperationFactory.setEventHandler(eventHandler);

            final SvnPatch patch = svnOperationFactory.createPatch();
            patch.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            patch.setPatchFile(patchFile);
            patch.run();

            Assert.assertFalse(file.exists());
            Assert.assertEquals("new", TestUtil.readFileContentsString(movedFile));

            final List<SVNEvent> events = eventHandler.getEvents();
            Assert.assertEquals(2, events.size());
            Assert.assertEquals(SVNEventAction.DELETE, events.get(0).getAction());
            Assert.assertEquals(file, events.get(0).getFile());
            Assert.assertEquals(SVNEventAction.ADD, events.get(1).getAction());
            Assert.assertEquals(movedFile, events.get(1).getFile());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testPatchGitFormat() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        
        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testPatchGitFormat", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String oldContent = "some line";
            final String newContent = "another line";

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", oldContent.getBytes());
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", newContent.getBytes());
            commitBuilder2.commit();

            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(new File("").getAbsoluteFile());
            diffGenerator.setUseGitFormat(true);
            final ByteArrayOutputStream output = new ByteArrayOutputStream();

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSource(SvnTarget.fromURL(url), SVNRevision.create(1), SVNRevision.create(2));
            diff.setDiffGenerator(diffGenerator);
            diff.setUseGitDiffFormat(true);
            diff.setOutput(output);
            diff.run();

            final String patchString = output.toString();

            final File directory = sandbox.createDirectory("tmp");
            final File patchFile = new File(directory, "patchFile");
            SVNFileUtil.writeToFile(patchFile, patchString, "UTF-8");

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();
            final SvnPatch patch = svnOperationFactory.createPatch();
            patch.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            patch.setPatchFile(patchFile);
            patch.run();

            final File file = workingCopy.getFile("file");
            final String fileContent = SVNFileUtil.readFile(file);

            Assert.assertEquals(newContent, fileContent);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testApplyGitPatchToDirectory() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testApplyGitPatchToDirectory", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final String oldContent = "some line";
            final String newContent = "another line";
            
            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", oldContent.getBytes());
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", newContent.getBytes());
            commitBuilder2.commit();

            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(new File("").getAbsoluteFile());
            diffGenerator.setUseGitFormat(true);
            final ByteArrayOutputStream output = new ByteArrayOutputStream();

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSource(SvnTarget.fromURL(url), SVNRevision.create(1), SVNRevision.create(2));
            diff.setDiffGenerator(diffGenerator);
            diff.setUseGitDiffFormat(true);
            diff.setOutput(output);
            diff.run();

            final String patchString = output.toString();

            final File directory = sandbox.createDirectory("tmp");
            final File patchFile = new File(directory, "patchFile");
            SVNFileUtil.writeToFile(patchFile, patchString, "UTF-8");

            final File file = new File(directory, "file");
            SVNFileUtil.writeToFile(file, oldContent, "UTF-8");

            final ArrayList<SVNPatchTargetInfo> targetInfos = new ArrayList<SVNPatchTargetInfo>();
            final ISvnPatchContext patchContext = new DirectoryPatchContext(directory);
            final SvnPatchFile svnPatchFile = SvnPatchFile.openReadOnly(patchFile);
            try {
                org.tmatesoft.svn.core.internal.wc2.patch.SvnPatch patch;
                do {
                    patch = org.tmatesoft.svn.core.internal.wc2.patch.SvnPatch.parseNextPatch(svnPatchFile, false, false);
                    if (patch != null) {
                        final boolean dryRun = false;
                        final int stripCount = 0;
                        final SvnPatchTarget target = SvnPatchTarget.applyPatch(patch, directory, stripCount, targetInfos, patchContext, true, true, null);

                        if (target.hasTextChanges() || target.isAdded() || target.getMoveTargetAbsPath() != null || target.isDeleted()) {
                            target.installPatchedTarget(directory, dryRun, patchContext, targetInfos);
                        }
                        if (target.hasPropChanges() && !target.isDeleted()) {
                            target.installPatchedPropTarget(dryRun, patchContext);
                        }
                    }
                } while (patch != null);

                final String actualContent = TestUtil.readFileContentsString(file);
                Assert.assertEquals(newContent, actualContent);
            } finally {
                svnPatchFile.close();
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testApplyGitPatchToDirectorySymlinks() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testApplyGitPatchToDirectorySymlinks", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("symlink1", "link symlink/target1".getBytes());
            commitBuilder1.addFile("symlink2", "link symlink/target2".getBytes());
            commitBuilder1.setFileProperty("symlink2", SVNProperty.SPECIAL, SVNPropertyValue.create("*"));
            commitBuilder1.addFile("symlink3", "symlink/target3".getBytes());
            commitBuilder1.setFileProperty("symlink3", SVNProperty.SPECIAL, SVNPropertyValue.create("*"));
            commitBuilder1.addFile("symlink4", "random content4".getBytes());
            commitBuilder1.setFileProperty("symlink4", SVNProperty.SPECIAL, SVNPropertyValue.create("*"));
            commitBuilder1.addFile("symlink5", "link symlink/target5".getBytes());
            commitBuilder1.setFileProperty("symlink5", SVNProperty.SPECIAL, SVNPropertyValue.create("*"));
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.setFileProperty("symlink1", SVNProperty.SPECIAL, SVNPropertyValue.create("*"));
            commitBuilder2.setFileProperty("symlink2", SVNProperty.SPECIAL, null);
            commitBuilder2.changeFile("symlink3", "link symlink/target3".getBytes());
            commitBuilder2.changeFile("symlink4", "link random content4".getBytes());
            commitBuilder2.changeFile("symlink5", "symlink/target5".getBytes());
            commitBuilder2.commit();

            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(new File("").getAbsoluteFile());
            diffGenerator.setUseGitFormat(true);
            diffGenerator.setIgnoreProperties(false);
            final ByteArrayOutputStream output = new ByteArrayOutputStream();

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSource(SvnTarget.fromURL(url), SVNRevision.create(1), SVNRevision.create(2));
            diff.setDiffGenerator(diffGenerator);
            diff.setUseGitDiffFormat(true);
            diff.setOutput(output);
            diff.run();

            final String patchString = output.toString();

            final File directory = sandbox.createDirectory("tmp");
            final File patchFile = new File(directory, "patchFile");
            SVNFileUtil.writeToFile(patchFile, patchString, "UTF-8");

            final File symlink1 = new File(directory, "symlink1");
            final File symlink2 = new File(directory, "symlink2");
            final File symlink3 = new File(directory, "symlink3");
            final File symlink4 = new File(directory, "symlink4");
            final File symlink5 = new File(directory, "symlink5");

            TestUtil.writeFileContentsString(symlink1, "link symlink/target1");
            TestUtil.writeFileContentsString(symlink2, "link symlink/target2");
            TestUtil.writeFileContentsString(symlink3, "symlink/target3");
            TestUtil.writeFileContentsString(symlink4, "random content4");
            TestUtil.writeFileContentsString(symlink5, "link symlink/target5");

            final DirectoryPatchContext patchContext = new DirectoryPatchContext(directory);
            final SvnPatchFile svnPatchFile = SvnPatchFile.openReadOnly(patchFile);
            applyPatch(patchContext, svnPatchFile, directory);

            Assert.assertEquals("link symlink/target1", TestUtil.readFileContentsString(symlink1));
            Assert.assertEquals("link symlink/target2", TestUtil.readFileContentsString(symlink2));
            Assert.assertEquals("link symlink/target3", TestUtil.readFileContentsString(symlink3));
            Assert.assertEquals("link random content4", TestUtil.readFileContentsString(symlink4));
            Assert.assertEquals("symlink/target5", TestUtil.readFileContentsString(symlink5));
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testApplyBinaryPatchToDirectory() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testApplyBinaryPatchToDirectory", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final byte[] oldContent = {8, 7, 6, 5, 4, 3, 2, 1};
            final byte[] newContent = {15, 14, 13, 12, 12, 11, 10};

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("file", oldContent);
            commitBuilder1.setFileProperty("file", SVNProperty.MIME_TYPE, SVNPropertyValue.create("application/octet-stream"));
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("file", newContent);
            commitBuilder2.commit();

            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(new File("").getAbsoluteFile());
            diffGenerator.setUseGitFormat(true);
            diffGenerator.setIgnoreProperties(false);
            final ByteArrayOutputStream output = new ByteArrayOutputStream();

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setSource(SvnTarget.fromURL(url), SVNRevision.create(1), SVNRevision.create(2));
            diff.setDiffGenerator(diffGenerator);
            diff.setUseGitDiffFormat(true);
            diff.setOutput(output);
            diff.run();

            final String patchString = output.toString();

            final File directory = sandbox.createDirectory("tmp");
            final File patchFile = new File(directory, "patchFile");
            SVNFileUtil.writeToFile(patchFile, patchString, "UTF-8");

            final File file = new File(directory, "file");
            final FileOutputStream fileOutputStream = new FileOutputStream(file);
            try {
                fileOutputStream.write(oldContent);
                fileOutputStream.flush();
            } finally {
                SVNFileUtil.closeFile(fileOutputStream);
            }

            final DirectoryPatchContext patchContext = new DirectoryPatchContext(directory);
            final SvnPatchFile svnPatchFile = SvnPatchFile.openReadOnly(patchFile);
            applyPatch(patchContext, svnPatchFile, directory);

            final byte[] actualContent = SVNFileUtil.readFully(file);
            Assert.assertArrayEquals(newContent, actualContent);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private void applyPatch(DirectoryPatchContext patchContext, SvnPatchFile svnPatchFile, File directory) throws IOException, SVNException {
        final ArrayList<SVNPatchTargetInfo> targetInfos = new ArrayList<SVNPatchTargetInfo>();
        try {
            org.tmatesoft.svn.core.internal.wc2.patch.SvnPatch patch;
            do {
                patch = org.tmatesoft.svn.core.internal.wc2.patch.SvnPatch.parseNextPatch(svnPatchFile, false, false);
                if (patch != null) {
                    final boolean dryRun = false;
                    final int stripCount = 0;
                    final SvnPatchTarget target = SvnPatchTarget.applyPatch(patch, directory, stripCount, targetInfos, patchContext, true, true, null);

                    if (target.hasTextChanges() || target.isAdded() || target.getMoveTargetAbsPath() != null || target.isDeleted()) {
                        target.installPatchedTarget(directory, dryRun, patchContext, targetInfos);
                    }
                    if (target.hasPropChanges() && !target.isDeleted()) {
                        target.installPatchedPropTarget(dryRun, patchContext);
                    }
                }
            } while (patch != null);
        } finally {
            svnPatchFile.close();
        }
    }

    private String getTestName() {
        return getClass().getSimpleName();
    }

    private static class DirectoryPatchContext implements ISvnPatchContext {

        private final File directory;

        private DirectoryPatchContext(File directory) {
            this.directory = directory;
        }

        @Override
        public void resolvePatchTargetStatus(SvnPatchTarget patchTarget, File workingCopyDirectory, boolean followMoves, List<SVNPatchTargetInfo> targetsInfo) throws SVNException {
            final File path = patchTarget.getAbsPath();
            final SVNNodeKind nodeKind = SVNFileType.getNodeKind(SVNFileType.getType(path));

            //assume clean target
            patchTarget.setSkipped(false);
            patchTarget.setLocallyDeleted(false);
            patchTarget.setDbKind(nodeKind);
            patchTarget.setKindOnDisk(nodeKind);
            patchTarget.setSymlink(false);
            patchTarget.setRelPath(SVNFileUtil.skipAncestor(workingCopyDirectory, patchTarget.getAbsPath()));
        }

        @Override
        public File createTempFile(File workingCopyDirectory) throws SVNException {
            return SVNFileUtil.createTempFile("", "");
        }

        @Override
        public SVNProperties getActualProps(File absPath) throws SVNException {
            //ignore properties
            return new SVNProperties();
        }

        @Override
        public void setProperty(File absPath, String propertyName, SVNPropertyValue propertyValue) throws SVNException {
            //ignore properties
        }

        @Override
        public boolean isTextModified(File absPath, boolean exactComparison) throws SVNException {
            //assume clean target
            return false;
        }

        @Override
        public SVNNodeKind readKind(File absPath, boolean showDeleted, boolean showHidden) throws SVNException {
            //assume clean target
            return SVNFileType.getNodeKind(SVNFileType.getType(absPath));
        }

        @Override
        public Map<? extends String, ? extends byte[]> computeKeywords(File localAbsPath, SVNPropertyValue keywordsVal) throws SVNException {
            //ignore keywords
            return Collections.emptyMap();
        }

        @Override
        public ISVNEventHandler getEventHandler() {
            //ignore events
            return null;
        }

        @Override
        public void delete(File absPath) throws SVNException {
            SVNFileUtil.deleteFile(absPath);
        }

        @Override
        public void add(File absPath) throws SVNException {
        }

        @Override
        public void move(File absPath, File moveTargetAbsPath) throws SVNException {
            SVNFileUtil.moveFile(absPath, moveTargetAbsPath);
        }

        @Override
        public boolean isExecutable(File absPath) throws SVNException {
            return SVNFileUtil.isExecutable(absPath);
        }

        @Override
        public void setExecutable(File absPath, boolean executable) {
        }

        @Override
        public void translate(File patchedAbsPath, File dst, String charset, byte[] eol, Map<String, byte[]> keywords, boolean special, boolean expand) throws SVNException {
            SVNFileUtil.copyFile(patchedAbsPath, dst, false);
        }

        @Override
        public void copySymlink(File src, File dst) throws SVNException {
            SVNFileUtil.copy(src, dst, false, false);
        }

        @Override
        public void writeSymlinkContent(File absPath, String linkName) throws SVNException {
            SVNFileUtil.writeToFile(absPath, linkName, "UTF-8");
        }

        @Override
        public String readSymlinkContent(File absPath) throws SVNException {
            return SVNFileUtil.readFile(absPath);
        }

        @Override
        public SVNFileType getKindOnDisk(File file) {
            final SVNFileType fileType = SVNFileType.getType(file);
            return fileType == SVNFileType.SYMLINK ? SVNFileType.FILE : fileType;
        }

        @Override
        public File wasNodeMovedHere(File localAbsPath) throws SVNException {
            return null;
        }
    }
}
