/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 *
 * @author ludovic
 * @author sdumitriu
 */

package com.xpn.xwiki.plugin.laszlo;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.plugin.PluginApi;

import java.io.IOException;

public class LaszloPluginApi extends PluginApi {

    public LaszloPluginApi(LaszloPlugin plugin, XWikiContext context) {
            super(plugin, context);
        }

    public LaszloPlugin getLaszloPlugin() {
        if (hasProgrammingRights()) {
            return (LaszloPlugin) getPlugin();
        }
        return null;
    }

    public String getFileName(String name, String laszlocode) {
        return getLaszloPlugin().getFileName(name, laszlocode);
    }

    public String getLaszloURL(String name, String laszlocode) throws IOException, XWikiException {
        return getLaszloPlugin().getLaszloURL(name, laszlocode);
    }

    public String getLaszloFlash(String name, String width, String height, String laszlocode) throws IOException, XWikiException {
        return getLaszloPlugin().getLaszloFlash(name, width, height, laszlocode, context);
    }

    public void flushCache() {
        getLaszloPlugin().flushCache();
    }
}
