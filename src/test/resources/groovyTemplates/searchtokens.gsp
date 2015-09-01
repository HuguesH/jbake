{ "urlset" : [
<% sep = "" %><% alltags.each { tag -> %> <%=sep%>
        {
        "loc" : "${config.site_host}/tags/${tag}.html",
        "tokens" : "${tag}"
        }
<% sep = "," %>
<%}%>
<% sep = "" %><% published_content.each { content -> %> <%if (content.tokensbody != null) {%><%=sep%>
        {
        "loc" : "${config.site_host}/${content.uri}",
        "tokens-title" : "${content.tokenstitle}",
        "tags" : "${content.tags}",
        "tokens" : "${content.tokensbody}",
        "lastmod" : "${content.date.format("yyyy-MM-dd")}"
        }
<% sep = "," %><%}%><%}%>
        ],

        "dico" : [ ] }
