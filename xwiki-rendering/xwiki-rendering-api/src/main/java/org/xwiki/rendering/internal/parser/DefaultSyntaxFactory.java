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
package org.xwiki.rendering.internal.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.annotation.Requirement;
import org.xwiki.component.logging.AbstractLogEnabled;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.rendering.parser.ParseException;
import org.xwiki.rendering.parser.Parser;
import org.xwiki.rendering.parser.Syntax;
import org.xwiki.rendering.parser.SyntaxFactory;
import org.xwiki.rendering.parser.SyntaxType;

/**
 * @version $Id$
 * @since 1.5M2
 */
@Component
public class DefaultSyntaxFactory extends AbstractLogEnabled implements SyntaxFactory
{
    /**
     * Used to cut the syntax identifier into syntax name and syntax version.
     */
    private static final Pattern SYNTAX_PATTERN = Pattern.compile("(.*)\\/(.*)");

    /**
     * Used to lookup all the parsers.
     */
    @Requirement
    private ComponentManager componentManager;

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.rendering.parser.SyntaxFactory#createSyntaxFromIdString(java.lang.String)
     */
    public Syntax createSyntaxFromIdString(String syntaxIdAsString) throws ParseException
    {
        Matcher matcher = SYNTAX_PATTERN.matcher(syntaxIdAsString);
        if (!matcher.matches()) {
            throw new ParseException("Invalid Syntax format [" + syntaxIdAsString + "]");
        }

        String syntaxId = matcher.group(1);
        String version = matcher.group(2);

        // Use the id as both the human readable name and the technical id (since the syntax string doesn't contain
        // any information about the pretty name of a syntax type).
        SyntaxType syntaxType = new SyntaxType(syntaxId, syntaxId);

        return new Syntax(syntaxType, version);
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.xwiki.rendering.parser.SyntaxFactory#getAvailableSyntaxes()
     * @deprecated starting with 2.0M3 you should directly lookup the Parser using the Component Manager
     */
    public List<Syntax> getAvailableSyntaxes()
    {
        List<Syntax> parserSyntaxList = new ArrayList<Syntax>();
        List<Parser> parsers;
        try {
            parsers = this.componentManager.lookupList(Parser.class);
        } catch (ComponentLookupException e) {
            // TODO: Do we need a Rendering RuntimeException? Or should we throw a checked exception instead?
            throw new RuntimeException("Failed to lookup the list of available Parser Syntaxes", e);
        }

        for (Parser parser : parsers) {
            parserSyntaxList.add(parser.getSyntax());
        }

        return parserSyntaxList;
    }
}
