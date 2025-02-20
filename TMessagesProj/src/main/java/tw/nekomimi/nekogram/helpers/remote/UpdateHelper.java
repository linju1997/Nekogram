package tw.nekomimi.nekogram.helpers.remote;

import android.content.pm.PackageInfo;
import android.os.Build;

import com.google.gson.JsonSyntaxException;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildConfig;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tw.nekomimi.nekogram.Extra;
import tw.nekomimi.nekogram.NekoConfig;

public class UpdateHelper extends BaseRemoteHelper {
    public static final String UPDATE_TAG = BuildConfig.DEBUG ? "updatetest" : NekoConfig.isDirectApp() ? "updatev3" : ("update" + BuildConfig.BUILD_TYPE + "v3");

    private int installedVersion;
    private String installedAbi;

    UpdateHelper() {
        try {
            var pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            installedVersion = pInfo.versionCode / 10;
            switch (pInfo.versionCode % 10) {
                case 1:
                case 3:
                    installedAbi = "arm-v7a";
                    break;
                case 2:
                case 4:
                    installedAbi = "x86";
                    break;
                case 5:
                case 7:
                    installedAbi = "arm64-v8a";
                    break;
                case 6:
                case 8:
                    installedAbi = "x86_64";
                    break;
                case 0:
                case 9:
                    installedAbi = "universal";
                    break;
            }
        } catch (Exception ignore) {
            installedVersion = -1;
            installedAbi = "universal";
        }
    }

    /**
     * @param date {long} - date in milliseconds
     */
    public static String formatDateUpdate(long date) {
        long epoch;
        try {
            PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            epoch = pInfo.lastUpdateTime;
        } catch (Exception e) {
            epoch = 0;
        }
        if (date <= epoch) {
            return LocaleController.formatString("LastUpdateNever", R.string.LastUpdateNever);
        }
        try {
            Calendar rightNow = Calendar.getInstance();
            int day = rightNow.get(Calendar.DAY_OF_YEAR);
            int year = rightNow.get(Calendar.YEAR);
            rightNow.setTimeInMillis(date);
            int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
            int dateYear = rightNow.get(Calendar.YEAR);

            if (dateDay == day && year == dateYear) {
                if (Math.abs(System.currentTimeMillis() - date) < 60000L) {
                    return LocaleController.formatString("LastUpdateRecently", R.string.LastUpdateRecently);
                }
                return LocaleController.formatString("LastUpdateFormatted", R.string.LastUpdateFormatted, LocaleController.formatString("TodayAtFormatted", R.string.TodayAtFormatted,
                        LocaleController.getInstance().formatterDay.format(new Date(date))));
            } else if (dateDay + 1 == day && year == dateYear) {
                return LocaleController.formatString("LastUpdateFormatted", R.string.LastUpdateFormatted, LocaleController.formatString("YesterdayAtFormatted", R.string.YesterdayAtFormatted,
                        LocaleController.getInstance().formatterDay.format(new Date(date))));
            } else if (Math.abs(System.currentTimeMillis() - date) < 31536000000L) {
                String format = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime,
                        LocaleController.getInstance().formatterDayMonth.format(new Date(date)),
                        LocaleController.getInstance().formatterDay.format(new Date(date)));
                return LocaleController.formatString("LastUpdateDateFormatted", R.string.LastUpdateDateFormatted, format);
            } else {
                String format = LocaleController.formatString("formatDateAtTime", R.string.formatDateAtTime,
                        LocaleController.getInstance().formatterYear.format(new Date(date)),
                        LocaleController.getInstance().formatterDay.format(new Date(date)));
                return LocaleController.formatString("LastUpdateDateFormatted", R.string.LastUpdateDateFormatted, format);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return "LOC_ERR";
    }

    private static final class InstanceHolder {
        private static final UpdateHelper instance = new UpdateHelper();
    }

    public static UpdateHelper getInstance() {
        return InstanceHolder.instance;
    }

    @Override
    protected void onError(String text, Delegate delegate) {
        delegate.onTLResponse(null, text);
    }

    @Override
    protected String getTag() {
        return UPDATE_TAG;
    }

    @SuppressWarnings("ConstantConditions")
    private int getPreferredAbiFile(Map<String, Integer> files) {
        for (String abi : Build.SUPPORTED_ABIS) {
            if (files.containsKey(abi)) {
                return files.get(abi);
            }
        }
        if (files.containsKey(installedAbi)) {
            return files.get(installedAbi);
        } else {
            return files.get("universal");
        }
    }

    private Update getShouldUpdateVersion(List<String> responses) {
        int maxVersion = installedVersion;
        Update ref = null;
        for (var string : responses) {
            try {
                var update = GSON.fromJson(string, Update.class);
                int version_code = update.versionCode;
                if (version_code > maxVersion) {
                    maxVersion = version_code;
                    ref = update;
                }
            } catch (JsonSyntaxException e) {
                FileLog.e(e);
            }
        }
        return ref;
    }

    private void getNewVersionMessagesCallback(Delegate delegate, Update json,
                                               HashMap<String, Integer> ids, TLObject response) {
        var update = new TLRPC.TL_help_appUpdate();
        update.version = json.version;
        update.can_not_skip = json.canNotSkip;
        if (json.url != null) {
            update.url = json.url;
            update.flags |= 4;
        }
        if (response != null) {
            var res = (TLRPC.messages_Messages) response;
            getMessagesController().removeDeletedMessagesFromArray(Extra.UPDATE_CHANNEL_ID, res.messages);
            var messages = new HashMap<Integer, TLRPC.Message>();
            for (var message : res.messages) {
                messages.put(message.id, message);
            }

            if (ids.containsKey("file")) {
                var file = messages.get(ids.get("file"));
                if (file != null && file.media != null) {
                    update.document = file.media.document;
                    update.flags |= 2;
                }
            }
            if (ids.containsKey("message")) {
                var message = messages.get(ids.get("message"));
                if (message != null) {
                    update.text = message.message;
                    update.entities = message.entities;
                }
            }
            if (ids.containsKey("sticker")) {
                var sticker = messages.get(ids.get("sticker"));
                if (sticker != null && sticker.media != null) {
                    update.sticker = sticker.media.document;
                    update.flags |= 8;
                }
            }
        }
        delegate.onTLResponse(update, null);
    }

    @Override
    protected void onLoadSuccess(ArrayList<String> responses, Delegate delegate) {
        var update = getShouldUpdateVersion(responses);
        if (update == null) {
            delegate.onTLResponse(null, null);
            return;
        }

        var ids = new HashMap<String, Integer>();
        if (update.message != null) {
            ids.put("message", update.message);
        }
        if (update.sticker != null) {
            ids.put("sticker", update.sticker);
        }
        if (update.files != null) {
            ids.put("file", getPreferredAbiFile(update.files));
        }

        if (ids.isEmpty()) {
            getNewVersionMessagesCallback(delegate, update, null, null);
        } else {
            var req = new TLRPC.TL_channels_getMessages();
            req.channel = getMessagesController().getInputChannel(-Extra.UPDATE_CHANNEL_ID);
            req.id = new ArrayList<>(ids.values());
            getConnectionsManager().sendRequest(req, (response1, error1) -> {
                if (error1 == null) {
                    getNewVersionMessagesCallback(delegate, update, ids, response1);
                } else {
                    delegate.onTLResponse(null, error1.text);
                }
            });
        }
    }

    public void checkNewVersionAvailable(Delegate delegate) {
        load(delegate);
        ConfigHelper.getInstance().load();
    }

    public static class Update {
        @SerializedName("can_not_skip")
        @Expose
        public Boolean canNotSkip;
        @SerializedName("version")
        @Expose
        public String version;
        @SerializedName("version_code")
        @Expose
        public Integer versionCode;
        @SerializedName("sticker")
        @Expose
        public Integer sticker;
        @SerializedName("message")
        @Expose
        public Integer message;
        @SerializedName("files")
        @Expose
        public Map<String, Integer> files;
        @SerializedName("url")
        @Expose
        public String url;
    }
}
