/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.annotate;

import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vcs.annotate.*;
import com.intellij.openapi.vcs.history.VcsFileRevision;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.text.DateFormatUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnRevisionNumber;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.history.SvnFileRevision;
import org.tmatesoft.svn.core.wc.SVNRevision;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 6/20/12
 * Time: 11:33 AM
 */
public abstract class BaseSvnFileAnnotation extends FileAnnotation {
  protected final String myContents;
  protected final VcsRevisionNumber myBaseRevision;
  private final MyPartiallyCreatedInfos myInfos;

  protected final SvnVcs myVcs;
  private final Map<Long, SvnFileRevision> myRevisionMap = new HashMap<Long, SvnFileRevision>();

  private final LineAnnotationAspect DATE_ASPECT = new SvnAnnotationAspect(LineAnnotationAspect.DATE, true) {

    @Override
    public String getValue(@NotNull LineInfo info) {
      return DateFormatUtil.formatPrettyDate(info.getDate());
    }
  };

  private final LineAnnotationAspect REVISION_ASPECT = new SvnAnnotationAspect(LineAnnotationAspect.REVISION, false) {

    @Override
    public String getValue(@NotNull LineInfo info) {
      return String.valueOf(info.getRevision());
    }
  };

  private final LineAnnotationAspect ORIGINAL_REVISION_ASPECT = new SvnAnnotationAspect(SvnBundle.message("annotation.original.revision"), false) {
    @Override
    public String getValue(int lineNumber) {
      final long value = myInfos.originalRevision(lineNumber);
      return (value == -1) ? "" : String.valueOf(value);
    }

    @Override
    protected long getRevision(int lineNum) {
      return myInfos.originalRevision(lineNum);
    }

    @Override
    public String getTooltipText(int lineNumber) {
      // TODO: Check what is the difference in returning "" or null
      if (!myInfos.isValid(lineNumber)) return "";

      LineInfo info = myInfos.get(lineNumber);
      if (info == null) return null;

      SvnFileRevision revision = myRevisionMap.get(info.getRevision());
      return revision != null ? XmlStringUtil.escapeString("Revision " + info.getRevision() + ": " + revision.getCommitMessage()) : "";
    }
  };

  private final LineAnnotationAspect AUTHOR_ASPECT = new SvnAnnotationAspect(LineAnnotationAspect.AUTHOR, true) {

    @Override
    public String getValue(@NotNull LineInfo info) {
      return info.getAuthor();
    }
  };

  private final SvnConfiguration myConfiguration;
  private boolean myShowMergeSources;
  // null if full annotation
  private SvnRevisionNumber myFirstRevisionNumber;

  public void setRevision(final long revision, final SvnFileRevision svnRevision) {
    myRevisionMap.put(revision, svnRevision);
  }

  public SvnFileRevision getRevision(final long revision) {
    return myRevisionMap.get(revision);
  }

  public void setFirstRevision(SVNRevision svnRevision) {
    myFirstRevisionNumber = new SvnRevisionNumber(svnRevision);
  }

  public SvnRevisionNumber getFirstRevisionNumber() {
    return myFirstRevisionNumber;
  }

  static class LineInfo {
    private final Date myDate;
    private final long myRevision;
    private final String myAuthor;

    public LineInfo(final Date date, final long revision, final String author) {
      myDate = date;
      myRevision = revision;
      myAuthor = author;
    }

    public Date getDate() {
      return myDate;
    }

    public long getRevision() {
      return myRevision;
    }

    public String getAuthor() {
      return myAuthor;
    }
  }

  public BaseSvnFileAnnotation(final SvnVcs vcs, final String contents, final VcsRevisionNumber baseRevision) {
    super(vcs.getProject());
    myVcs = vcs;
    myContents = contents;
    myBaseRevision = baseRevision;
    myConfiguration = SvnConfiguration.getInstance(vcs.getProject());
    myShowMergeSources = myConfiguration.isShowMergeSourcesInAnnotate();

    myInfos = new MyPartiallyCreatedInfos();
  }

  public LineAnnotationAspect[] getAspects() {
    return new LineAnnotationAspect[]{REVISION_ASPECT, DATE_ASPECT, AUTHOR_ASPECT};
  }

  public String getToolTip(final int lineNumber) {
    final LineInfo info = myInfos.getOrNull(lineNumber);
    if (info == null) return "";

    SvnFileRevision revision = myRevisionMap.get(info.getRevision());
    if (revision != null) {
      String prefix = myInfos.getAnnotationSource(lineNumber).showMerged() ? "Merge source revision" : "Revision";

      return prefix + " " + info.getRevision() + ": " + revision.getCommitMessage();
    }
    return "";
  }

  public String getAnnotatedContent() {
    return myContents;
  }

  public void setLineInfo(final int lineNumber, final Date date, final long revision, final String author,
                             @Nullable final Date mergeDate, final long mergeRevision, @Nullable final String mergeAuthor) {
    myInfos.appendNumberedLineInfo(lineNumber, date, revision, author, mergeDate, mergeRevision, mergeAuthor);
  }

  public void appendLineInfo(final Date date, final long revision, final String author,
                             @Nullable final Date mergeDate, final long mergeRevision, @Nullable final String mergeAuthor) {
    myInfos.appendNumberedLineInfo(date, revision, author, mergeDate, mergeRevision, mergeAuthor);
  }

  @Nullable
  public VcsRevisionNumber originalRevision(final int lineNumber) {
    SvnFileRevision revision = myInfos.isValid(lineNumber) ? myRevisionMap.get(myInfos.originalRevision(lineNumber)) : null;

    return revision != null ? revision.getRevisionNumber() : null;
  }

  public VcsRevisionNumber getLineRevisionNumber(final int lineNumber) {
    LineInfo info = myInfos.getOrNull(lineNumber);

    return info != null && info.getRevision() >= 0 ? new SvnRevisionNumber(SVNRevision.create(info.getRevision())) : null;
  }

  @Override
  public Date getLineDate(int lineNumber) {
    LineInfo info = myInfos.getOrNull(lineNumber);

    return info != null ? info.getDate() : null;
  }

  public List<VcsFileRevision> getRevisions() {
    final List<VcsFileRevision> result = new ArrayList<VcsFileRevision>(myRevisionMap.values());
    Collections.sort(result, new Comparator<VcsFileRevision>() {
      public int compare(final VcsFileRevision o1, final VcsFileRevision o2) {
        return o2.getRevisionNumber().compareTo(o1.getRevisionNumber());
      }
    });
    return result;
  }

  public boolean revisionsNotEmpty() {
    return ! myRevisionMap.isEmpty();
  }

  @Nullable
  public AnnotationSourceSwitcher getAnnotationSourceSwitcher() {
    if (! myShowMergeSources) return null;
    return new AnnotationSourceSwitcher() {
      @NotNull
      public AnnotationSource getAnnotationSource(int lineNumber) {
        return myInfos.getAnnotationSource(lineNumber);
      }

      public boolean mergeSourceAvailable(int lineNumber) {
        return myInfos.mergeSourceAvailable(lineNumber);
      }

      @NotNull
      public LineAnnotationAspect getRevisionAspect() {
        return ORIGINAL_REVISION_ASPECT;
      }

      @NotNull
      public AnnotationSource getDefaultSource() {
        return AnnotationSource.getInstance(myShowMergeSources);
      }

      public void switchTo(AnnotationSource source) {
        myInfos.setShowMergeSource(source.showMerged());
      }
    };
  }

  @Override
  public int getLineCount() {
    return myInfos.size();
  }

  @Override
  public VcsKey getVcsKey() {
    return SvnVcs.getKey();
  }

  private abstract class SvnAnnotationAspect extends LineAnnotationAspectAdapter {

    public SvnAnnotationAspect(String id, boolean showByDefault) {
      super(id, showByDefault);
    }

    protected long getRevision(final int lineNum) {
      final LineInfo lineInfo = myInfos.get(lineNum);
      return (lineInfo == null) ? -1 : lineInfo.getRevision();
    }

    @Override
    protected void showAffectedPaths(int lineNum) {
      if (myInfos.isValid(lineNum)) {
        final long revision = getRevision(lineNum);
        if (revision >= 0) {
          showAllAffectedPaths(new SvnRevisionNumber(SVNRevision.create(revision)));
        }
      }
    }

    @Override
    public String getValue(int lineNumber) {
      LineInfo info = myInfos.getOrNull(lineNumber);

      return info == null ? "" : getValue(info);
    }

    public String getValue(@NotNull LineInfo info) {
      return "";
    }
  }

  protected abstract void showAllAffectedPaths(SvnRevisionNumber number);

  private static class MyPartiallyCreatedInfos {
    private boolean myShowMergeSource;
    private final Map<Integer, LineInfo> myMappedLineInfo;
    private final Map<Integer, LineInfo> myMergeSourceInfos;
    private int myMaxIdx;

    private MyPartiallyCreatedInfos() {
      myMergeSourceInfos = new HashMap<Integer, LineInfo>();
      myMappedLineInfo = new HashMap<Integer, LineInfo>();
      myMaxIdx = 0;
    }

    void setShowMergeSource(boolean showMergeSource) {
      myShowMergeSource = showMergeSource;
    }

    int size() {
      return myMaxIdx + 1;
    }

    void appendNumberedLineInfo(final Date date, final long revision, final String author,
                               @Nullable final Date mergeDate, final long mergeRevision, @Nullable final String mergeAuthor) {
      appendNumberedLineInfo(myMaxIdx + 1, date, revision, author, mergeDate, mergeRevision, mergeAuthor);
    }

    void appendNumberedLineInfo(final int lineNumber, final Date date, final long revision, final String author,
                               @Nullable final Date mergeDate, final long mergeRevision, @Nullable final String mergeAuthor) {
      if (date == null) return;
      if (myMappedLineInfo.get(lineNumber) != null) return;
      myMaxIdx = (myMaxIdx < lineNumber) ? lineNumber : myMaxIdx;
      myMappedLineInfo.put(lineNumber, new LineInfo(date, revision, author));
      if (mergeDate != null) {
        myMergeSourceInfos.put(lineNumber, new LineInfo(mergeDate, mergeRevision, mergeAuthor));
      }
    }

    LineInfo get(final int idx) {
      if (myShowMergeSource) {
        final LineInfo lineInfo = myMergeSourceInfos.get(idx);
        if (lineInfo != null) {
          return lineInfo;
        }
      }
      return myMappedLineInfo.get(idx);
    }

    @Nullable
    LineInfo getOrNull(int lineNumber) {
      return isValid(lineNumber) ? get(lineNumber) : null;
    }

    private boolean isValid(int lineNumber) {
      return lineNumber >= 0 && lineNumber < size();
    }

    AnnotationSource getAnnotationSource(final int line) {
      return myShowMergeSource ? AnnotationSource.getInstance(myMergeSourceInfos.containsKey(line)) : AnnotationSource.LOCAL;
    }

    public long originalRevision(final int line) {
      LineInfo info = line < size() ? myMappedLineInfo.get(line) : null;

      return info == null ? -1 : info.getRevision();
    }

    public boolean mergeSourceAvailable(int lineNumber) {
      return myMergeSourceInfos.containsKey(lineNumber);
    }
  }

  @Nullable
  @Override
  public VcsRevisionNumber getCurrentRevision() {
    return myBaseRevision;
  }
}
