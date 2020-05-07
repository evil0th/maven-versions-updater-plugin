package com.sahara.intellij.plugin.mvn;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixBase;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.impl.source.xml.XmlTagImpl;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.Processor;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementsInspection;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.dom.MavenDomProjectProcessorUtils;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchResult;
import org.jetbrains.idea.maven.indices.MavenArtifactSearcher;
import org.jetbrains.idea.maven.model.MavenCoordinate;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;

import java.util.Arrays;
import java.util.List;

import static org.jetbrains.idea.maven.dom.MavenDomUtil.getProjectName;


/**
 * maven dependencies version update inspection
 *
 * @author liao
 * Create on 2020/5/7 14:33
 */
public class MavenDependenciesVersionUpdateInspection extends DomElementsInspection<MavenDomProjectModel> {
    public MavenDependenciesVersionUpdateInspection() {
        super(MavenDomProjectModel.class);
    }

    /**
     * This method is called internally in {@link DomElementAnnotationsManager#checkFileElement(com.intellij.util.xml.DomFileElement, DomElementsInspection, boolean)}
     * it should add some problems to the annotation holder. The default implementation performs recursive tree traversal, and calls
     * {@link #checkDomElement(com.intellij.util.xml.DomElement, DomElementAnnotationHolder, DomHighlightingHelper)} for each element.
     *
     * @param domFileElement file element to check
     * @param holder         the place to store problems
     */
    @Override
    public void checkFileElement(DomFileElement<MavenDomProjectModel> domFileElement, DomElementAnnotationHolder holder) {
        checkManagedDependencies(domFileElement, holder);
        checkDependencies(domFileElement, holder);
    }

    private static void checkDependencies(@NotNull DomFileElement<MavenDomProjectModel> domFileElement,
                                          @NotNull DomElementAnnotationHolder holder) {
        process(domFileElement, holder, false);
    }


    private static void checkManagedDependencies(@NotNull DomFileElement<MavenDomProjectModel> domFileElement,
                                                 @NotNull DomElementAnnotationHolder holder) {
        process(domFileElement, holder, true);
    }

    /**
     * process check
     *
     * @param domFileElement file element to check
     * @param holder         the place to store problems
     * @param managed        is managed dependencies
     */
    private static void process(@NotNull DomFileElement<MavenDomProjectModel> domFileElement,
                                @NotNull DomElementAnnotationHolder holder,
                                boolean managed) {
        MavenDomProjectModel projectModel = domFileElement.getRootElement();
        List<MavenDomDependency> dependencies;
        if (managed) {
            dependencies = projectModel.getDependencyManagement().getDependencies().getDependencies();
        } else {
            dependencies = projectModel.getDependencies().getDependencies();
        }
        MavenArtifactSearcher searcher = new MavenArtifactSearcher();
        Processor<MavenDomProjectModel> processor = mavenDomProjectModel -> {
            for (MavenDomDependency dependency : dependencies) {
                String groupId = dependency.getGroupId().getStringValue();
                String artifactId = dependency.getArtifactId().getStringValue();
                if (null == groupId || null == artifactId) {
                    continue;
                }
                String version = dependency.getVersion().getStringValue();
                if (domFileElement.getModule() != null && version != null) {
                    // remote https://package-search.services.jetbrains.com/api/search/idea/fulltext?query=${pattern}
                    String pattern = groupId + ":" + artifactId + ":";
                    List<MavenArtifactSearchResult> results = searcher.search(domFileElement.getModule().getProject(),
                            pattern, 1000);
                    for (MavenArtifactSearchResult result : results) {
                        MavenRepositoryArtifactInfo info = result.getSearchResults();
                        MavenCoordinate[] versions = info.getItems();
                        Arrays.sort(versions, (i1, i2) -> {
                            String v1 = i1.getVersion();
                            String v2 = i2.getVersion();
                            assert v2 != null;
                            assert v1 != null;
                            return new ComparableVersion(v2).compareTo(new ComparableVersion(v1));
                        });
                        // latest version item
                        MavenCoordinate coordinate = versions[0];
                        String latestVersion = coordinate.getVersion();
                        if (groupId.equals(coordinate.getGroupId())
                                && artifactId.equals(coordinate.getArtifactId())
                                && !version.equals(latestVersion)) {
                            System.out.println("[" + groupId + ":" + artifactId + "] found latest version : " + latestVersion);
                            addProblem(dependency, holder, projectModel, groupId, artifactId, version, latestVersion);
                        }
                    }
                }
            }
            return false;
        };
        MavenDomProjectProcessorUtils.processChildrenRecursively(projectModel, processor);
        MavenDomProjectProcessorUtils.processParentProjects(projectModel, processor);
    }

    /**
     * create problem after versions check
     *
     * @param dependency    current dependency
     * @param holder        the place to store problems
     * @param model         MavenDomProjectModel
     * @param groupId       g:
     * @param artifactId    a:
     * @param version       current version
     * @param latestVersion latest version
     */
    private static void addProblem(@NotNull MavenDomDependency dependency,
                                   @NotNull DomElementAnnotationHolder holder,
                                   MavenDomProjectModel model,
                                   String groupId,
                                   String artifactId,
                                   String version,
                                   String latestVersion) {
        LocalQuickFix fix = new LocalQuickFixBase("replace with " + latestVersion) {
            @Override
            public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
                if (descriptor.getPsiElement() instanceof XmlTagImpl) {
                    final XmlTag[] versions = ((XmlTagImpl) descriptor.getPsiElement()).findSubTags("version");
                    if (versions.length == 1) {
                        versions[0].getValue().setText(latestVersion);
                    }
                }
            }
        };
        String projectName = createLinkText(model, dependency);
        holder.createProblem(dependency, HighlightSeverity.WARNING,
                MessageBundle.message("inspection.version.outdated", projectName, groupId, artifactId, version, latestVersion),
                fix);
    }

    private static String createLinkText(@NotNull MavenDomProjectModel model, @NotNull MavenDomDependency dependency) {
        XmlTag tag = dependency.getXmlTag();
        if (tag == null) {
            return getProjectName(model);
        }
        VirtualFile file = tag.getContainingFile().getVirtualFile();
        if (file == null) {
            return getProjectName(model);
        }
        return String.format("<a href ='#navigation/%s:%s'>%s</a>",
                file.getPath(),
                tag.getTextRange().getStartOffset(),
                MavenDomUtil.getProjectName(model));
    }
}
