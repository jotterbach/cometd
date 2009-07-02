package org.cometd;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.eclipse.jetty.client.ContentExchange;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.io.Buffer;
import org.eclipse.jetty.io.ByteArrayBuffer;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

/**
 * @version $Revision$ $Date$
 */
public class XMLHttpRequestExchange extends ScriptableObject
{
    private CometExchange exchange;

    public XMLHttpRequestExchange()
    {
    }

    public void jsConstructor(Object threadModel, Scriptable scope, Scriptable thiz, Function function, String method, String url)
    {
        exchange = new CometExchange((ThreadModel)threadModel, scope, thiz, function, method, url);
    }

    public String getClassName()
    {
        return "XMLHttpRequestExchange";
    }

    public HttpExchange getHttpExchange()
    {
        return exchange;
    }

    public void jsFunction_addRequestHeader(String name, String value)
    {
        exchange.addRequestHeader(name, value);
    }

    public String jsGet_method()
    {
        return exchange.getMethod();
    }

    public void jsFunction_setRequestContent(String data) throws UnsupportedEncodingException
    {
        exchange.setRequestContent(data);
    }

    public int jsGet_readyState()
    {
        return exchange.getReadyState();
    }

    public String jsGet_responseText()
    {
        return exchange.getResponseText();
    }

    public int jsGet_responseStatus()
    {
        return exchange.getResponseStatus();
    }

    public String jsGet_responseStatusText()
    {
        return exchange.getResponseStatusText();
    }

    public void jsFunction_cancel()
    {
        exchange.cancel();
    }

    public String jsFunction_getAllResponseHeaders()
    {
        return exchange.getAllResponseHeaders();
    }

    public String jsFunction_getResponseHeader(String name)
    {
        return exchange.getResponseHeader(name);
    }

    public static class CometExchange extends ContentExchange
    {
        public enum ReadyState
        {
            UNSENT, OPENED, HEADERS_RECEIVED, LOADING, DONE
        }

        private final ThreadModel threads;
        private final Scriptable scope;
        private final Scriptable thiz;
        private final Function function;
        private final HttpFields responseFields = new HttpFields();
        private volatile boolean aborted;
        private volatile ReadyState readyState = ReadyState.UNSENT;
        private volatile String responseText;
        private volatile String responseStatusText;

        public CometExchange(ThreadModel threads, Scriptable scope, Scriptable thiz, Function function, String method, String url)
        {
            this.threads = threads;
            this.scope = scope;
            this.thiz = thiz;
            this.function = function;
            setMethod(method == null ? "GET" : method.toUpperCase());
            setURL(url);
            aborted = false;
            readyState = ReadyState.OPENED;
            responseStatusText = null;
            getRequestFields().clear();
            notifyReadyStateChange();
        }

        private void notifyReadyStateChange()
        {
            threads.execute(scope, thiz, function);
        }

        @Override
        public void cancel()
        {
            super.cancel();
            aborted = true;
            responseText = null;
            getRequestFields().clear();
            if (readyState == ReadyState.HEADERS_RECEIVED || readyState == ReadyState.LOADING)
            {
                readyState = ReadyState.DONE;
                notifyReadyStateChange();
            }
            readyState = ReadyState.UNSENT;
        }

        public int getReadyState()
        {
            return readyState.ordinal();
        }

        public String getResponseText()
        {
            return responseText;
        }

        public String getResponseStatusText()
        {
            return responseStatusText;
        }

        public void setRequestContent(String content) throws UnsupportedEncodingException
        {
            setRequestContent(new ByteArrayBuffer(content, "UTF-8"));
        }

        public String getAllResponseHeaders()
        {
            return responseFields.toString();
        }

        public String getResponseHeader(String name)
        {
            return responseFields.getStringField(name);
        }

        @Override
        protected void onResponseStatus(Buffer version, int status, Buffer statusText) throws IOException
        {
            super.onResponseStatus(version, status, statusText);
            this.responseStatusText = new String(statusText.asArray(), "UTF-8");
        }

        @Override
        protected void onResponseHeader(Buffer name, Buffer value) throws IOException
        {
            responseFields.add(name, value);
            super.onResponseHeader(name, value);
        }

        @Override
        protected void onResponseHeaderComplete() throws IOException
        {
            super.onResponseHeaderComplete();
            if (!aborted)
            {
                readyState = ReadyState.HEADERS_RECEIVED;
                notifyReadyStateChange();
            }
        }

        @Override
        protected void onResponseContent(Buffer buffer) throws IOException
        {
            super.onResponseContent(buffer);
            if (!aborted)
            {
                if (readyState != ReadyState.LOADING)
                {
                    readyState = ReadyState.LOADING;
                    notifyReadyStateChange();
                }
            }
        }

        @Override
        protected void onResponseComplete() throws IOException
        {
            super.onResponseComplete();
            if (!aborted)
            {
                responseText = getResponseContent();
                readyState = ReadyState.DONE;
                notifyReadyStateChange();
            }
        }
    }
}