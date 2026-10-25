package org.telegram.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.vpn.VpnController;
import org.telegram.messenger.vpn.VpnKeyInfo;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;
import org.telegram.utils.proxy.ProxySettings;

import java.util.ArrayList;
import java.util.List;

/** Proxy and VPN in one native settings surface; no second-level proxy/vpn pages. */
@SuppressLint("ViewConstructor")
public class OxNetworkActivity extends BaseFragment {

    private static final int ID_PROXY_ENABLED = 1;
    private static final int ID_PROXY_SERVER = 2;
    private static final int ID_PROXY_TYPE = 3;
    private static final int ID_VPN_ENABLED = 4;
    private static final int ID_VPN_ADD = 5;
    private static final int VPN_BASE = 1000;

    private UniversalRecyclerView listView;
    private ProxySettings.Type proxyType = ProxySettings.Type.SOCKS5;

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(false);
        actionBar.setTitle(LocaleController.getString(R.string.OxNetworkTitle));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });
        ProxySettings settings = currentProxySettings();
        proxyType = settings.getType();

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(getThemedColor(Theme.key_windowBackgroundGray));
        listView = new UniversalRecyclerView(this, this::fillItems, this::onClick, this::onLongClick);
        listView.adapter.setApplyBackground(false);
        listView.setSections();
        listView.setPadding(0, AndroidUtilities.dp(12), 0, AndroidUtilities.dp(12));
        listView.setClipToPadding(false);
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL));
        fragmentView = root;
        return root;
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        SharedPreferences prefs = MessagesController.getGlobalMainSettings();
        ProxySettings settings = currentProxySettings();
        boolean proxyOn = prefs.getBoolean("proxy_enabled", false) && settings.isValid();

        items.add(UItem.asHeader(LocaleController.getString(R.string.ProxySettings)));
        items.add(UItem.asCheck(ID_PROXY_ENABLED, LocaleController.getString(R.string.OxNetworkUseProxy)).setChecked(proxyOn));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_PROXY_SERVER, 0xFF1CA5ED, 0xFF1488E1,
                R.drawable.settings_data, LocaleController.getString(R.string.OxNetworkServer),
                settings.isValid() ? settings.getAddress() : LocaleController.getString(R.string.OxNetworkNotConfigured),
                settings.isValid() && settings.getPort() > 0 ? String.valueOf(settings.getPort()) : ""));
        items.add(SettingsActivity.SettingCell.Factory.of(ID_PROXY_TYPE, 0, 0,
                0, LocaleController.getString(R.string.OxNetworkType), null, proxyTypeName()));
        items.add(UItem.asShadow(LocaleController.getString(R.string.OxNetworkProxyInfo)));

        VpnController vc = VpnController.getInstance();
        items.add(UItem.asHeader(LocaleController.getString(R.string.VpnSettings)));
        items.add(UItem.asCheck(ID_VPN_ENABLED, LocaleController.getString(R.string.OxNetworkUseVpn)).setChecked(vc.isEnabled()));
        List<VpnKeyInfo> vpnList = vc.vpnList;
        if (vpnList.isEmpty()) {
            items.add(UItem.asShadow(LocaleController.getString(R.string.OxNetworkNoVpn)));
        } else {
            for (int i = 0; i < vpnList.size(); i++) {
                VpnKeyInfo info = vpnList.get(i);
                items.add(SettingsActivity.SettingCell.Factory.of(VPN_BASE + i, 0xFF55CA47, 0xFF27B434,
                        R.drawable.outline_shield_plain_24, info.name == null || info.name.isEmpty() ? "VPN" : info.name,
                        info.getType(), info == vc.currentVpn ? LocaleController.getString(R.string.OxNetworkInUse) : ""));
            }
        }
        items.add(SettingsActivity.SettingCell.Factory.of(ID_VPN_ADD, 0, 0,
                0, LocaleController.getString(R.string.OxNetworkAddVpn)));
        items.add(UItem.asShadow(LocaleController.getString(R.string.OxNetworkVpnInfo)));
    }

    private void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_PROXY_ENABLED) {
            boolean enabled = !MessagesController.getGlobalMainSettings().getBoolean("proxy_enabled", false);
            ProxySettings settings = currentProxySettings();
            if (enabled && !settings.isValid()) {
                showProxyDialog(null);
                return;
            }
            SharedPreferences.Editor e = MessagesController.getGlobalMainSettings().edit();
            e.putBoolean("proxy_enabled", enabled);
            e.apply();
            ConnectionsManager.setProxySettings(enabled, enabled ? settings : null);
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
            rebuild();
        } else if (item.id == ID_PROXY_SERVER) {
            showProxyDialog(currentProxySettings());
        } else if (item.id == ID_PROXY_TYPE) {
            proxyType = proxyType == ProxySettings.Type.SOCKS5 ? ProxySettings.Type.MTPROTO : ProxySettings.Type.SOCKS5;
            showProxyDialog(currentProxySettings());
        } else if (item.id == ID_VPN_ENABLED) {
            VpnController vc = VpnController.getInstance();
            vc.setEnabled(!vc.isEnabled());
            rebuild();
        } else if (item.id == ID_VPN_ADD) {
            showAddVpnDialog();
        } else if (item.id >= VPN_BASE) {
            VpnController vc = VpnController.getInstance();
            int index = item.id - VPN_BASE;
            if (index >= 0 && index < vc.vpnList.size()) {
                vc.selectVpn(vc.vpnList.get(index));
                if (!vc.isEnabled()) vc.setEnabled(true);
                rebuild();
            }
        }
    }

    private boolean onLongClick(UItem item, View view, int position, float x, float y) {
        if (item.id >= VPN_BASE) {
            VpnController vc = VpnController.getInstance();
            int index = item.id - VPN_BASE;
            if (index >= 0 && index < vc.vpnList.size() && getParentActivity() != null) {
                VpnKeyInfo info = vc.vpnList.get(index);
                new AlertDialog.Builder(getParentActivity())
                        .setTitle(LocaleController.getString(R.string.Delete))
                        .setMessage(info.name)
                        .setPositiveButton(LocaleController.getString(R.string.Delete), (d, w) -> {
                            vc.deleteVpn(info);
                            rebuild();
                        })
                        .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                        .show();
                return true;
            }
        }
        return false;
    }

    private ProxySettings currentProxySettings() {
        if (SharedConfig.currentProxy != null && SharedConfig.currentProxy.settings.isValid()) {
            return SharedConfig.currentProxy.settings;
        }
        return ProxySettings.fromSharedPreferences(MessagesController.getGlobalMainSettings());
    }

    private String proxyTypeName() {
        switch (proxyType) {
            case MTPROTO: return "MTProto";
            case WEB: return "Web";
            default: return "SOCKS5";
        }
    }

    private void showProxyDialog(ProxySettings current) {
        if (getParentActivity() == null) return;
        Context ctx = getParentActivity();
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(24);
        wrap.setPadding(pad, AndroidUtilities.dp(8), pad, 0);
        final EditText server = field(ctx, wrap, LocaleController.getString(R.string.OxNetworkServerHint));
        final EditText port = field(ctx, wrap, LocaleController.getString(R.string.OxNetworkPortHint));
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        final EditText user = field(ctx, wrap, LocaleController.getString(R.string.OxNetworkUserHint));
        final EditText pass = field(ctx, wrap, LocaleController.getString(R.string.OxNetworkPasswordHint));
        if (current != null) {
            server.setText(current.getAddress());
            if (current.getPort() > 0) port.setText(String.valueOf(current.getPort()));
            user.setText(current.getUser());
            pass.setText(proxyType == ProxySettings.Type.MTPROTO ? current.getSecret() : current.getPassword());
        }
        new AlertDialog.Builder(ctx)
                .setTitle(LocaleController.getString(R.string.ProxySettings))
                .setView(wrap)
                .setPositiveButton(LocaleController.getString(R.string.Save), (d, w) -> {
                    int p;
                    try { p = Integer.parseInt(port.getText().toString().trim()); } catch (Exception e) { p = 1080; }
                    ProxySettings settings = ProxySettings.builder()
                            .setType(proxyType)
                            .setAddress(server.getText().toString().trim())
                            .setPort(p)
                            .setUser(user.getText().toString().trim())
                            .setPassword(proxyType == ProxySettings.Type.MTPROTO ? "" : pass.getText().toString())
                            .setSecret(proxyType == ProxySettings.Type.MTPROTO ? pass.getText().toString() : "")
                            .build();
                    if (!settings.isValid()) return;
                    SharedConfig.ProxyInfo info = new SharedConfig.ProxyInfo(settings);
                    SharedConfig.proxyList.add(0, info);
                    SharedConfig.currentProxy = info;
                    SharedConfig.saveProxyList();
                    SharedPreferences.Editor e = MessagesController.getGlobalMainSettings().edit();
                    settings.toSharedPreferences(e);
                    e.putBoolean("proxy_enabled", true);
                    e.apply();
                    ConnectionsManager.setProxySettings(true, settings);
                    NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.proxySettingsChanged);
                    rebuild();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private EditText field(Context ctx, LinearLayout wrap, String hint) {
        EditText et = new EditText(ctx);
        et.setHint(hint);
        et.setSingleLine(true);
        wrap.addView(et, LayoutHelper.createLinear(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0, 6, 0, 6));
        return et;
    }

    private void showAddVpnDialog() {
        if (getParentActivity() == null) return;
        Context ctx = getParentActivity();
        LinearLayout wrap = new LinearLayout(ctx);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = AndroidUtilities.dp(24);
        wrap.setPadding(pad, AndroidUtilities.dp(8), pad, 0);
        final EditText name = field(ctx, wrap, LocaleController.getString(R.string.OxNetworkVpnNameHint));
        final EditText key = field(ctx, wrap, LocaleController.getString(R.string.OxNetworkVpnKeyHint));
        key.setSingleLine(false);
        key.setMinLines(3);
        new AlertDialog.Builder(ctx)
                .setTitle(LocaleController.getString(R.string.OxNetworkAddVpn))
                .setView(wrap)
                .setPositiveButton(LocaleController.getString(R.string.Add), (d, w) -> {
                    String k = key.getText().toString().trim();
                    if (k.isEmpty()) return;
                    String n = name.getText().toString().trim();
                    VpnKeyInfo info = new VpnKeyInfo(n.isEmpty() ? "VPN" : n, k);
                    VpnController vc = VpnController.getInstance();
                    vc.addVpn(info);
                    vc.selectVpn(info);
                    rebuild();
                })
                .setNegativeButton(LocaleController.getString(R.string.Cancel), null)
                .show();
    }

    private void rebuild() {
        if (listView != null) listView.adapter.update(true);
    }
}
