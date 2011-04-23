package org.protege.owl.codegeneration.inference;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.protege.owl.codegeneration.HandledDatatypes;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDatatype;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.vocab.XSDVocabulary;

public class ReasonerBasedInference implements CodeGenerationInference {
	private OWLOntology ontology;
	private OWLReasoner reasoner;
	private Map<OWLClass, Set<OWLObjectProperty>> objectPropertyMap             = new HashMap<OWLClass, Set<OWLObjectProperty>>();
	private Map<OWLClass, Set<OWLDataProperty>> dataPropertyMap                 = new HashMap<OWLClass, Set<OWLDataProperty>>();

	public ReasonerBasedInference(OWLOntology ontology, OWLReasoner reasoner) {
		this.ontology = ontology;
		this.reasoner = reasoner;
		analyzeProperties();
	}
	
	private void analyzeProperties() {
		OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
		for (OWLObjectProperty p : ontology.getObjectPropertiesInSignature()) {
			OWLClassExpression someCE = factory.getOWLObjectSomeValuesFrom(p, factory.getOWLThing());
			Set<OWLClass> disjoints = reasoner.getDisjointClasses(someCE).getFlattened();
			for (OWLClass cls : ontology.getClassesInSignature()) {
				if (!disjoints.contains(cls)) {
					addToMap(objectPropertyMap, cls, p);
				}
			}
		}
		for (OWLDataProperty p : ontology.getDataPropertiesInSignature()) {
			OWLClassExpression someCE = factory.getOWLDataSomeValuesFrom(p, factory.getTopDatatype());
			Set<OWLClass> disjoints = reasoner.getDisjointClasses(someCE).getFlattened();
			for (OWLClass cls : ontology.getClassesInSignature()) {
				if (!disjoints.contains(cls)) {
					addToMap(dataPropertyMap, cls, p);
				}
			}
		}
	}
	
	private <X, Y> void addToMap(Map<X, Set<Y>> map, X x, Y y) {
		Set<Y> ys = map.get(x);
		if (ys == null) {
			ys = new HashSet<Y>();
			map.put(x, ys);
		}
		ys.add(y);
	}
	
	public Collection<OWLClass> getOwlClasses() {
		Set<OWLClass> classes = new HashSet<OWLClass>(ontology.getClassesInSignature());
		classes.removeAll(reasoner.getUnsatisfiableClasses().getEntities());
		return classes;
	}
	
	public Collection<OWLNamedIndividual> getIndividuals(OWLClass owlClass) {
		return reasoner.getInstances(owlClass, false).getFlattened();
	}
	
	public Collection<OWLClass> getSuperClasses(OWLClass owlClass) {
		return reasoner.getSuperClasses(owlClass, true).getFlattened();
	}
	
	public Collection<OWLClass> getTypes(OWLNamedIndividual i) {
		return reasoner.getTypes(i, true).getFlattened();
	}

	public Collection<OWLObjectProperty> getObjectPropertiesForClass(OWLClass cls) {
		Set<OWLObjectProperty> properties = objectPropertyMap.get(cls);
		if (properties == null) {
			return Collections.emptySet();
		}
		else {
			return Collections.unmodifiableSet(properties);
		}
	}
	
	@Override
	public Collection<OWLClass> getRange(OWLClass cls, OWLObjectProperty p) {
		OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
		OWLClassExpression values = factory.getOWLObjectSomeValuesFrom(factory.getOWLObjectInverseOf(p), cls);
		return reasoner.getSuperClasses(values, true).getFlattened();
	}
	
	public Collection<OWLDataProperty> getDataPropertiesForClass(OWLClass cls) {
		Set<OWLDataProperty> properties = dataPropertyMap.get(cls);
		if (properties == null) {
			return Collections.emptySet();
		}
		else {
			return Collections.unmodifiableSet(properties);
		}
	}
	

	public OWLDatatype getRange(OWLClass cls, OWLDataProperty p) {
		OWLDataFactory factory = ontology.getOWLOntologyManager().getOWLDataFactory();
		for (HandledDatatypes handled : HandledDatatypes.values()) {
			OWLDatatype dt = factory.getOWLDatatype(handled.getIri());
			OWLClassExpression hasValueOfSomeOtherType 
			              = factory.getOWLObjectIntersectionOf(
			            		            cls,
											factory.getOWLObjectComplementOf(factory.getOWLDataAllValuesFrom(p, dt))
											);
			if (!reasoner.isSatisfiable(hasValueOfSomeOtherType)) {
				return dt;
			}
		}
		return null;
	}

}
