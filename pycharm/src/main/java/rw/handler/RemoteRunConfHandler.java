package rw.handler;

import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.process.BaseProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import rw.action.RunType;
import rw.audit.RwSentry;
import rw.config.Config;
import rw.config.ConfigManager;
import rw.consts.Const;
import rw.consts.Stage;
import rw.handler.PythonRunConfHandler;

import java.io.File;
import java.lang.reflect.Method;


public class RemoteRunConfHandler extends PythonRunConfHandler {
    public static final String USER_ID_ENV = "RW_USERID";  //  # RwRender: public static final String USER_ID_ENV = "{{ ctx.env_vars.user_id }}";  //

    public RemoteRunConfHandler(RunConfiguration runConf) {
        super(runConf);
    }

    public void onProcessStarted(RunContentDescriptor descriptor) {
        try {
            BaseProcessHandler<?> processHandler = (BaseProcessHandler<?>) descriptor.getProcessHandler();
            assert processHandler != null;
            Object process = processHandler.getProcess();

            Method addRemoteTunnel = process.getClass().getMethod("addRemoteTunnel", int.class, String.class, int.class);
            Method getSession = process.getClass().getMethod("getSession");

            Object session  = getSession.invoke(process);
            Method getHost = session.getClass().getMethod("getHost");

            String host = (String) getHost.invoke(session);

            addRemoteTunnel.invoke(process, this.session.getPort(), host, this.session.getPort());
        } catch (Exception e) {
            RwSentry.get().captureException(e, true);
        }
    }

    protected File getPackagesRootDir() {
        String ret = "/root/.reloadium/package";

        if(Const.get().stage != Stage.PROD) {
            ret += "_" + Const.get().stage.value;
        }

        return new File(ret);
    }

    @Override
    public void beforeRun(RunType runType) {
        super.beforeRun(runType);

        ConfigManager.get().createIfNotExists();
        Config config = ConfigManager.get().getConfig();

        this.runConf.getEnvs().put(USER_ID_ENV, config.user.uuid);
    }

    @NotNull
    @Override
    public String convertPathToLocal(@NotNull String remotePath, boolean warnMissing) {
        if (this.runConf.getMappingSettings() == null){
            Messages.showErrorDialog(this.project, "Path mappings are missing", "Missing Path Mappings");
            return remotePath;
        }

        String ret = this.runConf.getMappingSettings().convertToLocal(remotePath);

        if(warnMissing && ret.equals(remotePath)) {
            ApplicationManager.getApplication().invokeLater(() -> {
                Messages.showErrorDialog(this.project, String.format("Could not convert remote path %s to the local one. " +
                        "Add path mappings to resolve this", remotePath), "Missing Path Mappings");
            });
        }
        return ret;
    }

    @NotNull
    @Override
    public String convertPathToRemote(@NotNull String localPath, boolean warnMissing) {
        if (this.runConf.getMappingSettings() == null) {
            Messages.showErrorDialog(this.project, "Path mappings are missing", "Missing Path Mappings");
            return localPath;
        }

        String ret = this.runConf.getMappingSettings().convertToRemote(localPath);

        if(warnMissing && ret.equals(localPath)) {
            ApplicationManager.getApplication().invokeLater(() -> {
                Messages.showErrorDialog(this.project, String.format("Could not convert local path %s to the remote one. " +
                        "Add path mappings to resolve this", localPath), "Missing Path Mappings");
            });
        }
        return ret;
    }
}