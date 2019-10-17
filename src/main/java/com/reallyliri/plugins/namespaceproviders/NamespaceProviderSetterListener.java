package com.reallyliri.plugins.namespaceproviders;

import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.jetbrains.rider.model.RdCustomLocation;
import com.jetbrains.rider.model.RdProjectDescriptor;
import com.jetbrains.rider.model.RdProjectFolderDescriptor;
import com.jetbrains.rider.model.RdSolutionDescriptor;
import com.jetbrains.rider.projectView.nodes.ProjectModelNode;
import com.jetbrains.rider.projectView.views.solutionExplorer.SolutionExplorerCustomization;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public class NamespaceProviderSetterListener extends SolutionExplorerCustomization {
    private static final Logger log = Logger.getInstance(NamespaceProviderSetterListener.class);
    private Map<String, Set<String>> directoriesPathsByProject = new HashMap<>();

    public NamespaceProviderSetterListener(@NotNull Project project) {
        // its called project, but actually its a solution, probably due to it being project in other IDEA products
        super(project);
    }

    @NotNull
    @Override
    public List<AbstractTreeNode<?>> getChildren(@NotNull ProjectModelNode parentNode) {
        try {
            listen(parentNode);
        } catch (Throwable ex) {
            log.error(String.format("NamespaceProviderSetterListener error on node %s", parentNode.getName()), ex);
        }
        return super.getChildren(parentNode); // always returns empty, but its fine
    }

    private void listen(ProjectModelNode parentNode) throws IOException {
        if (parentNode.getDescriptor() instanceof RdSolutionDescriptor) {
            parentNode.getChildren(false, false).stream()
                .filter(node -> node.getDescriptor() instanceof RdProjectDescriptor)
                .forEach(projectNode -> {
                    String projectPath = projectPath(projectNode);
                    String projectName = Paths.get(projectPath).getFileName().toString();
                    if (!directoriesPathsByProject.containsKey(projectName)) {
                        directoriesPathsByProject.put(projectName, new HashSet<>());
                    }
                });
            return;
        }

        String projectPath = projectPath(parentNode);
        String projectName = Paths.get(projectPath).getFileName().toString();
        Set<String> projectDirectoriesPaths = directoriesPathsByProject.get(projectName);
        Set<String> currentDirectoryNodesPaths = parentNode.getChildren(false, false).stream()
            .filter(node -> node.getDescriptor() instanceof RdProjectFolderDescriptor)
            .map(this::nodeFullRelativePath)
            .collect(Collectors.toSet());

        Set<String> addedDirectories = difference(currentDirectoryNodesPaths, projectDirectoriesPaths);
        // TODO - add support for removed directories - not that simple due to current node being only one level of the tree

        if (addedDirectories.isEmpty()) {
            return;
        }

        Path dotSettingsFilePath = dotSettingsFilePath(projectPath);
        String dotSettingsContent;
        if (!new File(dotSettingsFilePath.toString()).exists()) {
            dotSettingsContent = dotSettingsEmptyContent;
        } else {
            dotSettingsContent = new String(Files.readAllBytes(dotSettingsFilePath), StandardCharsets.UTF_8);
        }

        for (String directoryPath : addedDirectories) {
            String notNamespaceProviderLine = notNamespaceProviderLine(directoryPath);
            if (dotSettingsContent.contains(notNamespaceProviderLine)) {
                continue;
            }
            log.info(String.format("Setting directory '%s' of project '%s' as not a namespace provider", directoryPath, getProject().getName()));
            dotSettingsContent = dotSettingsContent.replace("</wpf:ResourceDictionary>", "\t" + notNamespaceProviderLine + "\n</wpf:ResourceDictionary>");
        }

        Files.write(dotSettingsFilePath, dotSettingsContent.getBytes(StandardCharsets.UTF_8));

        projectDirectoriesPaths.addAll(addedDirectories);
    }

    private static <T> Set<T> difference(final Set<T> setOne, final Set<T> setTwo) {
        Set<T> result = new HashSet<>(setOne);
        result.removeIf(setTwo::contains);
        return result;
    }

    private Path dotSettingsFilePath(String projectPath) {
        return Paths.get(String.format("%s.DotSettings", projectPath));
    }

    private String projectPath(ProjectModelNode node) {
        while (!(node.getDescriptor() instanceof RdProjectDescriptor)) {
            node = node.getParent();
        }
        return ((RdCustomLocation) node.getDescriptor().getLocation()).getCustomLocation();
    }

    private String nodeFullRelativePath(ProjectModelNode node) {
        ProjectModelNode parent = node.getParent();
        if (parent == null || !(parent.getDescriptor() instanceof RdProjectFolderDescriptor)) {
            return node.getName();
        }
        return String.format("%s/%s", nodeFullRelativePath(parent), node.getName());
    }

    private String notNamespaceProviderLine(String relativeDirectoryPath) {
        return
            "<s:Boolean x:Key=\"/Default/CodeInspection/NamespaceProvider/NamespaceFoldersToSkip/=" +
                relativeDirectoryPath.replace(File.separator, "_005C") +
                "/@EntryIndexedValue\">True</s:Boolean>";
    }

    private final String dotSettingsEmptyContent =
        "<wpf:ResourceDictionary xml:space=\"preserve\" xmlns:x=\"http://schemas.microsoft.com/winfx/2006/xaml\" xmlns:s=\"clr-namespace:System;assembly=mscorlib\" xmlns:ss=\"urn:shemas-jetbrains-com:settings-storage-xaml\" xmlns:wpf=\"http://schemas.microsoft.com/winfx/2006/xaml/presentation\">\n"
            + "</wpf:ResourceDictionary>";
}
