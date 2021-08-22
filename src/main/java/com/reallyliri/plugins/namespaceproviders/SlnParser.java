package com.reallyliri.plugins.namespaceproviders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SlnParser {
    private static final Pattern pattern = Pattern.compile("\"[^\"]+\\.csproj\",", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    public static Set<Path> extractProjectPaths(Path slnPath) throws IOException {
        Set<Path> projectPaths = new HashSet<>();
        String content = Files.readString(slnPath);
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String csprojRelativePath = matcher.group().replace("\"", "").replace(",", "").replace("\\", "/");
            projectPaths.add(Paths.get(slnPath.getParent().toString(), csprojRelativePath));
        }
        return projectPaths;
    }
}
