package com.reallyliri.plugins.namespaceproviders;

import com.intellij.icons.AllIcons.Actions;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.jetbrains.rider.model.RdSolutionDescriptor;
import com.jetbrains.rider.projectView.views.solutionExplorer.SolutionExplorerCustomization;
import com.jetbrains.rider.projectView.workspace.ProjectModelEntity;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.NotNull;

public class NamespaceProviderSetterListener extends SolutionExplorerCustomization {
    private static final Logger log = Logger.getInstance(NamespaceProviderSetterListener.class);
    private final Applier applier = new Applier();
    private ProjectModelEntity root;

    public NamespaceProviderSetterListener(@NotNull Project project) {
        // its called project, but actually its a solution, probably due to it being project in other IDEA products
        super(project);
    }

    @Override
    public void addPrimaryToolbarActions(@NotNull DefaultActionGroup actionGroup) {
        actionGroup.addSeparator();
        actionGroup.addAction(new ApplyAction());
        actionGroup.addSeparator();
        super.addPrimaryToolbarActions(actionGroup);
    }

    private class ApplyAction extends AnAction {
        public ApplyAction() {
            super("Apply No-Namespace-Providers", "Apply configuration to mark all directories as non-namespace-providers", Actions.ProjectDirectory);
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
            try {
                if (root != null) {
                    int updated = applier.apply(root, true);
                    Notifications.Bus.notify(new Notification(
                        Notifications.SYSTEM_MESSAGES_GROUP_ID,
                        "No-Namespace-Providers",
                        updated > 0 ? String.format("%d directories were updated", updated) : "No updates were detected",
                        NotificationType.INFORMATION
                    ));
                }
            } catch (Throwable ex) {
                log.error(String.format("NamespaceProviderSetterListener error on solution %s", getProject()), ex);
            }
        }
    }

    @NotNull
    @Override
    public List<AbstractTreeNode<?>> getChildren(@NotNull ProjectModelEntity parentNode) {
        try {
            listen(parentNode);
        } catch (Throwable ex) {
            if (!ex.getMessage().contains("EventQueue.isDispatchThread()=false")) {
                log.error(String.format("NamespaceProviderSetterListener error on node %s", parentNode.getName()), ex);
            }
        }
        return super.getChildren(parentNode); // always returns empty, but its fine
    }

    private void listen(ProjectModelEntity parentNode) throws IOException {
        if (parentNode.getDescriptor() instanceof RdSolutionDescriptor) {
            root = parentNode;
        }
        applier.apply(parentNode, false);
    }
}
