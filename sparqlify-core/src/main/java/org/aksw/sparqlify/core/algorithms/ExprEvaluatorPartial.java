package org.aksw.sparqlify.core.algorithms;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.management.RuntimeErrorException;

import org.aksw.sparqlify.core.SparqlifyConstants;
import org.aksw.sparqlify.trash.ExprCopy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hp.hpl.jena.sparql.core.Var;
import com.hp.hpl.jena.sparql.expr.Expr;
import com.hp.hpl.jena.sparql.expr.ExprFunction;
import com.hp.hpl.jena.sparql.expr.ExprNotComparableException;
import com.hp.hpl.jena.sparql.expr.NodeValue;
import com.hp.hpl.jena.sparql.function.FunctionRegistry;
import com.hp.hpl.jena.sparql.util.ExprUtils;

/**
 * Evaluator for expressions.
 * Transforms SPARQL expressions so that they can be translated to SQL.
 * Concretely, this class attempts to remove all type constructors
 * from the expressions. 
 * 
 * 
 * 
 * 
 * 
 * If not all of an expressions' variables are bound, it tries to evaluate as much
 * as possible; hence the name "partial" evaluator.
 * 
 * 
 * 
 * 
 * 
 * 
 * @author Claus Stadler <cstadler@informatik.uni-leipzig.de>
 *
 */
public class ExprEvaluatorPartial
	implements ExprEvaluator
{
	
	private static final Logger logger = LoggerFactory.getLogger(ExprEvaluatorPartial.class);
	
	private FunctionRegistry registry;
	
	private FunctionRegistrySql sqlRegistry;
	
	/**
	 *  The transformer is called AFTER all of a functions arguments have been evaluated.
	 *  
	 */
	private ExprTransformer exprTransformer;
	
	public ExprEvaluatorPartial(FunctionRegistry registry, ExprTransformer exprTransformer) {
		this.registry = registry;
		this.exprTransformer = exprTransformer;
	}
	
	
	public static boolean isConstantsOnly(Iterable<Expr> exprs) {
		for(Expr expr : exprs) {
			if(!expr.isConstant()) {
				return false;
			}
		}
		
		return true;
	}
	
	public static boolean isConstantArgsOnly(ExprFunction fn) {
		
		boolean result = isConstantsOnly(fn.getArgs());

		return result;
	}
	
	public Expr eval(ExprFunction fn, Map<Var, Expr> binding) {
		List<Expr> evaledArgs = new ArrayList<Expr>();
		
		for(Expr arg : fn.getArgs()) {				
			Expr evaledArg = eval(arg, binding);
			
			// If an argument evaluated to type error, return type error
			if(evaledArg.equals(SparqlifyConstants.nvTypeError)) {
				return SparqlifyConstants.nvTypeError;
			}
			
			evaledArgs.add(evaledArg);
		}
		
		Expr newExpr = ExprCopy.getInstance().copy(fn, evaledArgs);
		Expr tmp = newExpr;
		
		if(exprTransformer != null && newExpr.isFunction()) {
			tmp = exprTransformer.transform(newExpr.getFunction());
		}
				
		
		
		// If some arguments are not constant, we can't evaluate
		// FIXME This is not true, we could still perform a partial evaluation
		// What is true, though, is, that Jena throws errors when evaluating exprs with unbound vars
		if(tmp.isFunction() && !ExprEvaluatorPartial.isConstantArgsOnly(tmp.getFunction())) {
			return tmp;
		}

		
		
		// Check if the function's IRI is registered
		// If not, don't try to evaluate the corresponding expression
		Set<String> builtInOps = new HashSet<String>(Arrays.asList("<=", "<", "=", "!=", ">", ">="));
		
		String fnIri = org.aksw.sparqlify.expr.util.ExprUtils.getFunctionId(fn); //fn.getFunctionIRI();			
		if(fnIri != null && !fnIri.isEmpty()) {
			if(!builtInOps.contains(fnIri) && registry.get(fnIri) == null) {
				return tmp;
			}
		}
		
		Expr result = tmp;
		
		try {
			result = ExprUtils.eval(tmp);
		} catch(ExprNotComparableException e) {
			return SparqlifyConstants.nvTypeError;
		}
		catch(Exception e) {
			// Failed to evaluate - use original value
			logger.warn("Failed to evaluate expr: " + tmp);
		}
		
		return result;
	}
	
	/*
	 * How to best add interceptors (callbacks with transformation) for certain functions?
	 * 
	 * e.g.: concat(foo, concat(?x...)) -> concat(foo, ?x)
	 * lang(rdfterm(2, ?x, ?y, '')) -> ?y
	 * 
	 * The main question is, whether to apply to callback before or after the arguments are evaluated.
	 * 
	 * -> After makes more sense: Then we have constant folder arguments 
	 */
	public Expr eval(Expr expr, Map<Var, Expr> binding) {
		
		
		if(expr == null) {
			throw new RuntimeException("Null expression should not happen");
		}
		
		//System.out.println(expr);
		
		Expr result = null;
		if(expr.isConstant()) {
			
			//result = ConstantExpander.transform(expr);
			result = expr;
			
		} else if(expr.isFunction()) {
			ExprFunction fn = expr.getFunction();
			
			result = eval(fn, binding);
		} else if(expr.isVariable()) {
			
			result = expr;
			
			if(binding != null) {
				Expr boundExpr = binding.get(expr.asVar());
				if(boundExpr != null) {
					result = eval(boundExpr, null); // Do not forward the binding
				}
			}
		} else {
			throw new RuntimeException("Unknown expression type encountered: " + expr);
		}

		return result;
	}

}
