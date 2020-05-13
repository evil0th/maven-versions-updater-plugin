package com.sahara.intellij.plugin.mvn;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixBase;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.util.Processor;
import com.intellij.util.xml.DomFileElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.highlighting.DomElementAnnotationHolder;
import com.intellij.util.xml.highlighting.DomElementAnnotationsManager;
import com.intellij.util.xml.highlighting.DomElementsInspection;
import com.intellij.util.xml.highlighting.DomHighlightingHelper;
import org.apache.maven.artifact.versioning.ComparableVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.model.MavenDomDependency;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jetbrains.idea.maven.dom.references.MavenPropertyPsiReference;
import org.jetbrains.idea.maven.dom.references.MavenPsiElementWrapper;
import org.jetbrains.idea.maven.indices.MavenArtifactSearchResult;
import org.jetbrains.idea.maven.indices.MavenArtifactSearcher;
import org.jetbrains.idea.maven.model.MavenCoordinate;
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo;
import org.jetbrains.idea.maven.server.MavenServerManager;
import org.jetbrains.idea.maven.utils.MavenLog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.util.containers.ContainerUtil.findInstance;


/**
 * maven dependencies version update inspection
 *
 * @author liao
 * Create on 2020/5/7 14:33
 */
public class MavenDependenciesVersionUpdateInspection extends DomElementsInspection<MavenDomProjectModel> {
    public final static String DEFINED_AS_PROPERTY = "/\\$\\{.*}/";
    public final static String DEFINED_AS_PROPERTY_START = "${";
    public final static String MAVEN_VERSION_35 = "3.5";
    public final static String VALUE_TO_CHECK = "\\$\\{(revision|sha1|changelist)}";

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
        process(domFileElement, holder);
    }

    /**
     * process check
     *
     * @param domFileElement file element to check
     * @param holder         the place to store problems
     */
    private static void process(@NotNull DomFileElement<MavenDomProjectModel> domFileElement,
                                @NotNull DomElementAnnotationHolder holder) {
        MavenDomProjectModel projectModel = domFileElement.getRootElement();
        Set<MavenDomDependency> dependencies = new HashSet<>(projectModel.getDependencies().getDependencies());
        dependencies.addAll(projectModel.getDependencyManagement().getDependencies().getDependencies());
        MavenLog.LOG.info(dependencies.size() + " dependencies found in current virtual file path : " + domFileElement.getFile().getVirtualFile().getPath());
        MavenArtifactSearcher searcher = new MavenArtifactSearcher();
        Processor<MavenDomProjectModel> processor = mavenDomProjectModel -> {
            int i = 0;
            for (MavenDomDependency dependency : dependencies) {
                i++;
                String groupId = dependency.getGroupId().getStringValue();
                String artifactId = dependency.getArtifactId().getStringValue();
                if (null == groupId || null == artifactId) {
                    continue;
                }
                String version = dependency.getVersion().getStringValue();
                System.out.println(i + "/" + dependencies.size() + " [" + groupId + ":" + artifactId + "] found :" + version);
                if (null == domFileElement.getModule() || null == version) {
                    continue;
                }
                // remote https://package-search.services.jetbrains.com/api/search/idea/fulltext?query=${pattern}
                String pattern = groupId + ":" + artifactId + ":";
                List<MavenArtifactSearchResult> results = searcher.search(domFileElement.getModule().getProject(),
                        pattern, 1000);
                for (MavenArtifactSearchResult result : results) {
                    MavenRepositoryArtifactInfo info = result.getSearchResults();
                    if (!(groupId.equals(info.getGroupId()) && artifactId.equals(info.getArtifactId()))) {
                        continue;
                    }
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
                    if (new ComparableVersion(latestVersion).compareTo(new ComparableVersion(version)) > 0) {
                        System.out.println("[" + groupId + ":" + artifactId + "] found latest version : " + latestVersion);
                        addProblem(dependency, holder, projectModel, groupId, artifactId, version, latestVersion);
                    }
                }
            }
            return false;
        };
        // process self only
        processor.process(projectModel);
//        MavenDomProjectProcessorUtils.processChildrenRecursively(projectModel, processor, true);
//        MavenDomProjectProcessorUtils.processParentProjects(projectModel, processor);
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
        if (model == null) {
            return;
        }

        GenericDomValue<String> domValue = dependency.getVersion();
        // ${xx.version}
        String unresolvedValue = domValue.getRawText();
        if (unresolvedValue == null) {
            return;
        }
        boolean maven35 = StringUtil.compareVersionNumbers(MavenServerManager.getInstance().getCurrentMavenVersion(), MAVEN_VERSION_35) >= 0;
        String valueToCheck = maven35 ? unresolvedValue.replaceAll(VALUE_TO_CHECK, "") : unresolvedValue;
        System.out.println("[" + groupId + ":" + artifactId + "] value to check : " + valueToCheck);

        LocalQuickFix fix = null;
        if (valueToCheck.contains(DEFINED_AS_PROPERTY_START)) {
            // resolved value defined in property, 1.2.3
            String resolvedValue = domValue.getStringValue();
            if (resolvedValue == null) {
                return;
            }

            if (unresolvedValue.equals(resolvedValue) || resolvedValue.contains(DEFINED_AS_PROPERTY_START)) {
                resolvedValue = resolveXmlElement(domValue.getXmlElement());
            }

            // find reference property
            MavenPropertyPsiReference psiReference = findInstance(domValue.getXmlElement().getReferences(), MavenPropertyPsiReference.class);
            if (psiReference == null) {
                return;
            }
            PsiElement resolvedElement = psiReference.resolve();
            if (resolvedElement == null) {
                return;
            }
            PsiElement psiElement = ((MavenPsiElementWrapper) resolvedElement).getWrappee();

            if (!unresolvedValue.equals(resolvedValue) && !isEmpty(resolvedValue)) {
                fix = new LocalQuickFixBase(MavenVersionUpdateBundle.message("replace.property.version", latestVersion)) {
                    @Override
                    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
                        if (psiElement instanceof XmlTag) {
                            ((XmlTag) psiElement).getValue().setText(latestVersion);
                        } else if (psiElement instanceof XmlText) {
                            ((XmlText) psiElement).setValue(latestVersion);
                        }
                    }
                };
            }
        } else {
            fix = new LocalQuickFixBase(MavenVersionUpdateBundle.message("replace.element.version", latestVersion)) {
                @Override
                public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
                    PsiElement psiElement = descriptor.getPsiElement();
                    if (psiElement instanceof XmlTag) {
                        ((XmlTag) psiElement).getValue().setText(latestVersion);
                    } else if (psiElement instanceof XmlText) {
                        ((XmlText) psiElement).setValue(latestVersion);
                    }
                }
            };
        }
        String projectName = createLinkText(model, dependency);
        holder.createProblem(dependency.getVersion(), HighlightSeverity.WARNING,
                MavenVersionUpdateBundle.message("inspection.version.outdated", projectName, groupId, artifactId, version, latestVersion), fix);
    }

    private static String createLinkText(@NotNull MavenDomProjectModel model, @NotNull MavenDomDependency dependency) {
        XmlTag tag = dependency.getXmlTag();
        if (tag == null) {
            return MavenDomUtil.getProjectName(model);
        }
        VirtualFile file = tag.getContainingFile().getVirtualFile();
        if (file == null) {
            return MavenDomUtil.getProjectName(model);
        }
        return String.format("<a href ='#navigation/%s:%s'>%s</a>",
                file.getPath(),
                tag.getTextRange().getStartOffset(),
                MavenDomUtil.getProjectName(model));
    }

    @Nullable
    private static String resolveXmlElement(@Nullable XmlElement xmlElement) {
        if (xmlElement == null) {
            return null;
        }
        MavenPropertyPsiReference psiReference = findInstance(xmlElement.getReferences(), MavenPropertyPsiReference.class);
        if (psiReference == null) {
            return null;
        }
        PsiElement resolvedElement = psiReference.resolve();
        if (!(resolvedElement instanceof MavenPsiElementWrapper)) {
            return null;
        }
        PsiElement xmlTag = ((MavenPsiElementWrapper) resolvedElement).getWrappee();
        if (!(xmlTag instanceof XmlTag)) {
            return null;
        }
        return ((XmlTag) xmlTag).getValue().getTrimmedText();
    }
}
