package org.aksw.sparqlify.core.rewrite.expr.transform;

import org.aksw.jena_sparql_api.views.E_RdfTerm;
import org.apache.jena.sparql.expr.E_Str;
import org.apache.jena.sparql.expr.Expr;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.vocabulary.XSD;

public class ExprTransformerStr
	extends ExprTransformerBase1
{

	@Override
	public E_RdfTerm transform(Expr orig, E_RdfTerm a) {
		
		Expr av = a.getLexicalValue();

		Expr c = new E_Str(av);
		
		E_RdfTerm result = E_RdfTerm.createTypedLiteral(c, NodeValue.makeString(XSD.xstring.getURI()));
		
		return result;
	}

}
