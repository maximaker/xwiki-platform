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
 */
package org.xwiki.gwt.wysiwyg.client;

import org.xwiki.gwt.dom.client.JavaScriptObject;
import org.xwiki.gwt.user.client.Config;
import org.xwiki.gwt.user.client.NativeActionHandler;
import org.xwiki.gwt.user.client.NativeAsyncCallback;
import org.xwiki.gwt.user.client.internal.DefaultConfig;
import org.xwiki.gwt.user.client.ui.rta.RichTextArea;
import org.xwiki.gwt.user.client.ui.rta.cmd.Command;
import org.xwiki.gwt.user.client.ui.rta.cmd.CommandManagerApi;
import org.xwiki.gwt.wysiwyg.client.converter.HTMLConverter;
import org.xwiki.gwt.wysiwyg.client.converter.HTMLConverterAsync;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.JsArrayString;
import com.google.gwt.event.logical.shared.CloseEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.DOM;
import com.google.gwt.user.client.Element;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.RootPanel;
import com.google.gwt.user.client.ui.Widget;

/**
 * This class exposes a {@link WysiwygEditor} to the native JavaScript code.
 * 
 * @version $Id$
 */
public class WysiwygEditorApi
{
    /**
     * The command used to submit the value of the rich text area.
     */
    public static final Command SUBMIT = new Command("submit");

    /**
     * The underlying {@link WysiwygEditor} which is exposed in native JavaScript code.
     */
    private WysiwygEditor editor;

    /**
     * The JavaScript object that exposes the command manager used by the rich text area.
     */
    private CommandManagerApi commandManagerApi;

    /**
     * The component used to convert the HTML generated by the WYSIWYG editor to source syntax.
     */
    private final HTMLConverterAsync converter = GWT.create(HTMLConverter.class);

    /**
     * Creates a new {@link WysiwygEditor} based on the given configuration object.
     * 
     * @param jsConfig the {@link JavaScriptObject} used to configure the newly created editor
     */
    public WysiwygEditorApi(JavaScriptObject jsConfig)
    {
        if (!isRichTextEditingSupported()) {
            return;
        }

        Config config = new DefaultConfig(jsConfig);

        // Get the element that will be replaced by the WYSIWYG editor.
        Element hook = DOM.getElementById(config.getParameter("hookId"));
        if (hook == null) {
            return;
        }

        // Prepare the DOM by creating a container for the editor.
        final Element container = DOM.createDiv();
        String containerId = hook.getId() + "_container" + Math.round(Math.random() * 1000);
        container.setId(containerId);
        hook.getParentElement().insertBefore(container, hook);

        editor = WysiwygEditorFactory.getInstance().newEditor(config);

        // Attach the editor to the browser's document.
        if (editor.getConfig().isDebugMode()) {
            RootPanel.get(containerId).add(new WysiwygEditorDebugger(editor));
        } else {
            RootPanel.get(containerId).add(editor.getUI());
        }

        // Cleanup when the window is closed. This way the HTML form elements generated on the server preserve their
        // index and thus can be cached by the browser.
        Window.addCloseHandler(new CloseHandler<Window>()
        {
            public void onClose(CloseEvent<Window> event)
            {
                if (editor != null) {
                    editor.destroy();
                }
                if (container.getParentNode() != null) {
                    container.getParentNode().removeChild(container);
                }
            }
        });
    }

    /**
     * @return {@code true} if the current browser supports rich text editing, {@code false} otherwise
     */
    public static boolean isRichTextEditingSupported()
    {
        RichTextArea textArea = new RichTextArea(null);
        return textArea.getFormatter() != null;
    }

    /**
     * Releases the editor so that it can be garbage collected before the page is unloaded. Call this method before the
     * editor is physically detached from the DOM document.
     */
    public void release()
    {
        if (editor != null) {
            // Logical detach.
            Widget container = editor.getUI();
            while (container.getParent() != null) {
                container = container.getParent();
            }
            RootPanel.detachNow(container);
            editor = null;
        }
    }

    /**
     * @return the plain HTML text area element used by the editor
     */
    public Element getPlainTextArea()
    {
        return editor == null ? null : editor.getPlainTextEditor().getTextArea().getElement();
    }

    /**
     * @return the rich text area element used by the editor
     */
    public Element getRichTextArea()
    {
        return editor == null ? null : editor.getRichTextEditor().getTextArea().getElement();
    }

    /**
     * Sends a request to the server to convert the HTML output of the rich text editor to source text and calls one of
     * the given functions when the response is received.
     * 
     * @param onSuccess the JavaScript function to call on success
     * @param onFailure the JavaScript function to call on failure
     */
    public void getSourceText(JavaScriptObject onSuccess, JavaScriptObject onFailure)
    {
        NativeAsyncCallback<String> callback = new NativeAsyncCallback<String>(onSuccess, onFailure);
        if (editor.getRichTextEditor().getTextArea().isEnabled()) {
            // We have to convert the HTML of the rich text area to source text.
            // Notify the plug-ins that the content of the rich text area is about to be submitted.
            editor.getRichTextEditor().getTextArea().getCommandManager().execute(SUBMIT);
            // Make the request to convert the submitted HTML to source text.
            converter.fromHTML(editor.getRichTextEditor().getTextArea().getCommandManager().getStringValue(SUBMIT),
                editor.getConfig().getSyntax(), callback);
        } else {
            // We take the source text from the plain text editor.
            callback.onSuccess(editor.getPlainTextEditor().getTextArea().getText());
        }
    }

    /**
     * @return a JavaScript object that exposes the command manager used by the rich text area
     */
    public CommandManagerApi getCommandManagerApi()
    {
        if (commandManagerApi == null) {
            commandManagerApi =
                CommandManagerApi.newInstance(editor.getRichTextEditor().getTextArea().getCommandManager());
        }
        return commandManagerApi;
    }

    /**
     * Creates an action handler that wraps the given JavaScript function and registers it for the specified action.
     * 
     * @param actionName the name of the action to listen to
     * @param jsHandler the JavaScript function to be called when the specified action occurs
     * @return the registration for the event, to be used for removing the handler
     */
    public HandlerRegistration addActionHandler(String actionName, JavaScriptObject jsHandler)
    {
        if (editor != null) {
            return editor.getRichTextEditor().getTextArea().addActionHandler(actionName,
                new NativeActionHandler(jsHandler));
        }
        return null;
    }

    /**
     * @param name the name of a configuration parameter
     * @return the value of the specified editor configuration parameter
     */
    public String getParameter(String name)
    {
        return editor.getConfigurationSource().getParameter(name);
    }

    /**
     * @return the list of editor configuration parameters
     */
    public JsArrayString getParameterNames()
    {
        JsArrayString parameterNames = JavaScriptObject.createArray().cast();
        for (String parameterName : editor.getConfigurationSource().getParameterNames()) {
            parameterNames.push(parameterName);
        }
        return parameterNames;
    }

    /**
     * Publishes the JavaScript API that can be used to create and control {@link WysiwygEditor}s.
     */
    public static native void publish()
    /*-{
        // We can't use directly the WysiwygEditorApi constructor because currently there's no way to access (as in save
        // a reference to) the GWT instance methods without having an instance.
        $wnd.WysiwygEditor = function(config) {
            if (typeof config == 'object') {
                this.instance = @org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::new(Lorg/xwiki/gwt/dom/client/JavaScriptObject;)(config);
            }
        }
        $wnd.WysiwygEditor.prototype.release = function() {
            this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::release()();
        }
        $wnd.WysiwygEditor.prototype.getPlainTextArea = function() {
            return this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::getPlainTextArea()();
        }
        $wnd.WysiwygEditor.prototype.getRichTextArea = function() {
            return this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::getRichTextArea()();
        }
        $wnd.WysiwygEditor.prototype.getSourceText = function(onSuccess, onFailure) {
            this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::getSourceText(Lorg/xwiki/gwt/dom/client/JavaScriptObject;Lorg/xwiki/gwt/dom/client/JavaScriptObject;)(onSuccess, onFailure);
        }
        $wnd.WysiwygEditor.prototype.getCommandManager = function() {
            return this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::getCommandManagerApi()();
        }
        $wnd.WysiwygEditor.prototype.addActionHandler = function(actionName, handler) {
            var registration = this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::addActionHandler(Ljava/lang/String;Lorg/xwiki/gwt/dom/client/JavaScriptObject;)(actionName, handler);
            return function() {
                if (registration) {
                    registration.@com.google.gwt.event.shared.HandlerRegistration::removeHandler()();
                    registration = null;
                }
            };
        }
        $wnd.WysiwygEditor.prototype.getParameter = function(name) {
            return this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::getParameter(Ljava/lang/String;)(name);
        }
        $wnd.WysiwygEditor.prototype.getParameterNames = function() {
            return this.instance.@org.xwiki.gwt.wysiwyg.client.WysiwygEditorApi::getParameterNames()();
        }
    }-*/;
}
