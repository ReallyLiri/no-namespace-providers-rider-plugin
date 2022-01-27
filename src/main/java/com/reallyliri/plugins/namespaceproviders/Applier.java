package com.reallyliri.plugins.namespaceproviders;

import com.google.common.collect.Streams;
import com.intellij.openapi.diagnostic.Logger;
import com.jetbrains.rider.model.RdCustomLocation;
import com.jetbrains.rider.model.RdProjectDescriptor;
import com.jetbrains.rider.model.RdProjectFolderDescriptor;
import com.jetbrains.rider.model.RdSolutionDescriptor;
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Applier {

    private static final Logger log = Logger.getInstance(Applier.class);

    private final Map<String, Set<String>> directoriesPathsByProject = new HashMap<>();
    private final Set<String> ignoredDirectoryNames = Set.of(
        ".idea",
        "node_modules",
        "vendor",
        "bin",
        "bld",
        "build",
        "logs",
        "obj",
        "target",
        "resources",
        "x64",
        "x86",
        "amd64",
        "netcoreapp2.1",
        "netcoreapp2.2",
        "netcoreapp3.0",
        "netcoreapp3.1",
        "net5.0",
        "net6.0"
    );

    public int applyFromProjects(Set<Path> csProjPaths) throws IOException {
        int updates = 0;
        for (Path csProjPath : csProjPaths) {
            updates += applyFromProject(csProjPath);
        }
        return updates;
    }

    private int applyFromProject(Path csProjPath) throws IOException {
        Set<Path> nodePaths = new HashSet<>();
        Path projDirectoryPath = csProjPath.getParent();
        collectChildFolders(projDirectoryPath, nodePaths);
        return apply(csProjPath.toString(), nodePaths.stream().map(projDirectoryPath::relativize).map(Path::toString).collect(Collectors.toSet()));
    }

    public int apply(ProjectModelEntity parentNode, boolean applyFullTree) throws IOException {
        return (parentNode.getDescriptor() instanceof RdSolutionDescriptor)
            ? applyRdSln(parentNode, applyFullTree)
            : applyNode(parentNode, false);
    }

    private int applyNode(ProjectModelEntity parentNode, boolean applyFullTree) throws IOException {
        String projectPath = projectPath(parentNode);
        if (projectPath == null) {
            return 0;
        }

        List<ProjectModelEntity> nodes = new ArrayList<>();
        collectChildFolders(parentNode, nodes, applyFullTree);

        Set<String> nodePaths = nodes.stream()
            .map(this::nodeFullRelativePath)
            .collect(Collectors.toSet());

        return apply(projectPath, nodePaths);
    }

    private void collectChildFolders(Path parentPath, Set<Path> children) throws IOException {
        Set<Path> directoryPaths = Files.walk(parentPath, 1)
            .filter(path -> !path.equals(parentPath))
            .filter(Files::isDirectory)
            .filter(path -> !ignoredDirectoryNames.contains(path.getFileName().toString().toLowerCase()))
            .collect(Collectors.toSet());
        for (Path directoryPath : directoryPaths) {
            children.add(directoryPath);
            collectChildFolders(directoryPath, children);
        }
    }

    private void collectChildFolders(ProjectModelEntity parentNode, List<ProjectModelEntity> nodes, boolean recursive) {
        List<ProjectModelEntity> children = Streams.stream(parentNode.getChildrenEntities().iterator())
            .filter(node -> node.getDescriptor() instanceof RdProjectFolderDescriptor)
            .filter(node -> !ignoredDirectoryNames.contains(node.getName().toLowerCase()))
            .collect(Collectors.toList());
        nodes.addAll(children);
        if (!recursive) {
            return;
        }
        for (ProjectModelEntity child : children) {
            collectChildFolders(child, nodes, true);
        }
    }

    private int applyRdSln(ProjectModelEntity slnNode, boolean applyFullTree) throws IOException {
        List<ProjectModelEntity> rdProjects = Streams.stream(slnNode.getChildrenEntities().iterator())
            .filter(node -> node.getDescriptor() instanceof RdProjectDescriptor)
            .collect(Collectors.toList());

        int updated = 0;
        for (ProjectModelEntity projectNode : rdProjects) {
            String projectPath = projectPath(projectNode);
            if (projectPath == null) {
                continue;
            }
            if (!directoriesPathsByProject.containsKey(projectPath)) {
                directoriesPathsByProject.put(projectPath, new HashSet<>());
            }
            if (applyFullTree) {
                updated += applyNode(projectNode, true);
            }
        }
        return updated;
    }

    private int apply(String projectPath, Set<String> subPaths) throws IOException {
        Set<String> existingPaths = directoriesPathsByProject.getOrDefault(projectPath, new HashSet<>());

        Set<String> addedDirectories = difference(subPaths, existingPaths);
        // TODO - add support for removed directories - not that simple due to current node being only one level of the tree

        if (addedDirectories.isEmpty()) {
            return 0;
        }

        Path dotSettingsFilePath = dotSettingsFilePath(projectPath);
        String dotSettingsContent;
        if (!new File(dotSettingsFilePath.toString()).exists()) {
            dotSettingsContent = dotSettingsEmptyContent;
        } else {
            dotSettingsContent = Files.readString(dotSettingsFilePath);
        }

        int updated = 0;
        for (String directoryPath : addedDirectories) {
            String notNamespaceProviderLine = notNamespaceProviderLine(directoryPath);
            if (dotSettingsContent.contains(notNamespaceProviderLine)) {
                continue;
            }
            log.info(String.format("Setting directory '%s' of project '%s' as not a namespace provider", directoryPath, projectPath));
            dotSettingsContent = dotSettingsContent.replace("</wpf:ResourceDictionary>", "\n\t" + notNamespaceProviderLine + "\n</wpf:ResourceDictionary>");
            updated++;
        }

        Files.write(dotSettingsFilePath, dotSettingsContent.getBytes(StandardCharsets.UTF_8));

        existingPaths.addAll(addedDirectories);
        return updated;
    }

    private static <T> Set<T> difference(final Set<T> setOne, final Set<T> setTwo) {
        Set<T> result = new HashSet<>(setOne);
        result.removeIf(setTwo::contains);
        return result;
    }

    private Path dotSettingsFilePath(String projectPath) {
        return Paths.get(String.format("%s.DotSettings", projectPath));
    }

    private String projectPath(ProjectModelEntity node) {
        while (node != null && !(node.getDescriptor() instanceof RdProjectDescriptor)) {
            node = node.getParentEntity();
        }
        if (node == null) {
            return null;
        }
        return ((RdCustomLocation) node.getDescriptor().getLocation()).getCustomLocation();
    }

    private String nodeFullRelativePath(ProjectModelEntity node) {
        ProjectModelEntity parent = node.getParentEntity();
        if (parent == null || !(parent.getDescriptor() instanceof RdProjectFolderDescriptor)) {
            return node.getName();
        }
        return String.format("%s/%s", nodeFullRelativePath(parent), node.getName());
    }

    private String notNamespaceProviderLine(String relativeDirectoryPath) {
        return
            "<s:Boolean x:Key=\"/Default/CodeInspection/NamespaceProvider/NamespaceFoldersToSkip/=" +
                relativeDirectoryPath.toLowerCase().replace("/", "_005C").replace("\\", "_005C") +
                "/@EntryIndexedValue\">True</s:Boolean>";
    }

    private final String dotSettingsEmptyContent =
        "<wpf:ResourceDictionary xml:space=\"preserve\" xmlns:x=\"http://schemas.microsoft.com/winfx/2006/xaml\" xmlns:s=\"clr-namespace:System;assembly=mscorlib\" xmlns:ss=\"urn:shemas-jetbrains-com:settings-storage-xaml\" xmlns:wpf=\"http://schemas.microsoft.com/winfx/2006/xaml/presentation\">\n"
            + "</wpf:ResourceDictionary>";
}
