/*
 * ============================================================================
 * GNU Lesser General Public License
 * ============================================================================
 *
 * XSD2Thrift
 * 
 * Copyright (C) 2009 Sergio Alvarez-Napagao http://www.sergio-alvarez.com
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307, USA.
 */
package com.github.tranchis.xsd2thrift;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import com.github.tranchis.xsd2thrift.marshal.IMarshaller;
import com.sun.xml.xsom.parser.XSOMParser;
import com.sun.xml.xsom.XSAttGroupDecl;
import com.sun.xml.xsom.XSAttributeDecl;
import com.sun.xml.xsom.XSAttributeUse;
import com.sun.xml.xsom.XSComplexType;
import com.sun.xml.xsom.XSElementDecl;
import com.sun.xml.xsom.XSFacet;
import com.sun.xml.xsom.XSModelGroup;
import com.sun.xml.xsom.XSParticle;
import com.sun.xml.xsom.XSRestrictionSimpleType;
import com.sun.xml.xsom.XSSchema;
import com.sun.xml.xsom.XSSchemaSet;
import com.sun.xml.xsom.XSSimpleType;
import com.sun.xml.xsom.XSTerm;
import com.sun.xml.xsom.XSType;

public class XSDParser implements ErrorHandler
{
	private File					f;
	private Map<String,Struct>		map;
	private Map<String,Enumeration>	enums;
	private Set<String>				keywords, basicTypes;
	private TreeMap<String, String>	xsdMapping;
	private IMarshaller				marshaller;
	private OutputStream			os;
	private String					namespace;
	
	public XSDParser(String stFile)
	{
		this.xsdMapping = new TreeMap<String,String>();
		init(stFile);
	}
	
	private void init(String stFile)
	{
		os = System.out;
		
		this.f = new File(stFile);
		map = new HashMap<String,Struct>();
		enums = new HashMap<String,Enumeration>();
		keywords = new TreeSet<String>();
		keywords.add("interface");
		keywords.add("is");
		keywords.add("class");
		keywords.add("optional");
		keywords.add("yield");
		keywords.add("abstract");
		keywords.add("required");
		keywords.add("volatile");
		keywords.add("transient");
		keywords.add("service");
		keywords.add("else");
		
		basicTypes = new TreeSet<String>();
		basicTypes.add("string");
		basicTypes.add("anyType");
		basicTypes.add("anyURI");
		basicTypes.add("anySimpleType");
		basicTypes.add("integer");
		basicTypes.add("positiveInteger");
		basicTypes.add("binary");
		basicTypes.add("boolean");
		basicTypes.add("decimal");
		basicTypes.add("byte");
		basicTypes.add("long");
		basicTypes.add("ID");
		basicTypes.add("IDREF");
		basicTypes.add("NMTOKEN");
		basicTypes.add("NMTOKENS");
//		basicTypes.add("BaseObject");
	}

	public XSDParser(String stFile, TreeMap<String,String> xsdMapping)
	{
		this.xsdMapping = xsdMapping;
		init(stFile);
	}
	
	public void parse() throws Exception
	{
		XSOMParser parser;
		
		parser = new XSOMParser();
		parser.setErrorHandler(this);
		parser.parse(f);
		
		interpretResult(parser.getResult());
		writeMap();
	}
	
	private void writeMap() throws Exception
	{
		Iterator<Struct>		its;
		Iterator<Field>			itf;
		Struct					st;
		Field					f;
		int						order;
		String					fname, type, enumValue;
		Set<Struct>				ss;
		Set<String>				declared, usedInEnums;
		Iterator<Enumeration>	ite;
		Iterator<String>		itg;
		Enumeration				en;
		boolean					bModified;
		
		os.write(marshaller.writeHeader(namespace).getBytes());
		
		st = createSuperObject();
//		map.put("BaseObject", st);
		
		if(!marshaller.isNestedEnums())
		{
			ite = enums.values().iterator();
			while(ite.hasNext())
			{
				en = ite.next();
				os.write(marshaller.writeEnumHeader(escape(en.getName())).getBytes());
				itg = en.iterator();
				order = 1;
				while(itg.hasNext())
				{
					os.write(marshaller.writeEnumValue(order, escape(itg.next())).getBytes());
					order = order + 1;
				}
				os.write(marshaller.writeEnumFooter().getBytes());
			}
		}
		
		ss = new HashSet<Struct>(map.values());
		declared = new TreeSet<String>(basicTypes);
		declared.addAll(enums.keySet());
		
		bModified = true;
		while(bModified && !ss.isEmpty())
		{
			bModified = false;
			its = map.values().iterator();
			while(its.hasNext())
			{
				st = its.next();
//				if(ss.contains(st))
//				{
//					System.out.println(st.getName() + ": " + st.getTypes());
//				}
				if(ss.contains(st) && declared.containsAll(st.getTypes()))
				{
					os.write(marshaller.writeStructHeader(escape(st.getName())).getBytes());
					itf = st.getFields().iterator();
					usedInEnums = new TreeSet<String>();
					order = 1;
					while(itf.hasNext())
					{
						f = itf.next();
						fname = f.getName();
						type = f.getType();
						
						if(marshaller.isNestedEnums() && enums.containsKey(type))
						{
							en = enums.get(type);
							enumValue = escape(en.getName());
							while(usedInEnums.contains(enumValue))
							{
								enumValue = "_" + enumValue;
							}
							usedInEnums.add(enumValue);
							os.write(marshaller.writeEnumHeader(enumValue).getBytes());
							itg = en.iterator();
							while(itg.hasNext())
							{
								enumValue = escape(itg.next());
								while(usedInEnums.contains(enumValue))
								{
									enumValue = "_" + enumValue;
								}
								usedInEnums.add(enumValue);
								os.write(marshaller.writeEnumValue(order, enumValue).getBytes());
								order = order + 1;
							}
							os.write(marshaller.writeEnumFooter().getBytes());
						}
						
						if(!map.keySet().contains(type) && !basicTypes.contains(type) && !enums.containsKey(type))
						{
							type = "binary";
						}
						if(type.equals(fname))
						{
							fname = "_" + fname;
						}
						if(marshaller.getTypeMapping(type) != null)
						{
							type = marshaller.getTypeMapping(type);
						}
						os.write(marshaller.writeStructParameter(order, f.isRequired(), f.isRepeat(), escape(fname), escape(type)).getBytes());
						order = order + 1;
					}
					os.write(marshaller.writeStructFooter().getBytes());
					declared.add(st.getName());
					
					ss.remove(st);
					bModified = true;
				}
			}
		}
		
		if(!ss.isEmpty())
		{
			throw new Exception();
		}
	}

	private Struct createSuperObject()
	{
		Struct				st, aux;
		Iterator<Struct>	its;
		
		st = new Struct("BaseObject");
		
		its = map.values().iterator();
		st.addField("baseObjectType", "string", true, false, null, xsdMapping);
		while(its.hasNext())
		{
			aux = its.next();
			st.addField(aux.getName(), aux.getName(),
				false, false, null, xsdMapping);
		}
		
		return st;
	}

	private String escape(String name)
	{
		String	res;
		
		res = name.replace('-', '_');
		res = res.replace('.', '_');
		if(res.charAt(0) >= '0' && res.charAt(0) <= '9')
		{
			res = '_' + res;
		}
		if(keywords.contains(res))
		{
			res = "_" + res;
		}
		
		return res;
	}

	private void interpretResult(XSSchemaSet sset)
	{
		XSSchema				xs;
		Iterator<XSSchema>		it;
		Iterator<XSElementDecl>	itt;
		XSElementDecl			el;
		
		it = sset.iterateSchema();
		while(it.hasNext())
		{
			xs = it.next();
			if(!xs.getTargetNamespace().endsWith("/XMLSchema"))
			{
				itt = xs.iterateElementDecls();
				while(itt.hasNext())
				{
					el = itt.next();
					interpretElement(el, sset);				
				}
			}
		}
	}

	private void interpretElement(XSElementDecl el, XSSchemaSet sset)
	{
		Struct			st;
		XSComplexType	cType;
		XSType			parent;
		XSSimpleType	xs;

		if(el.getType() instanceof XSComplexType && el.getType() != sset.getAnyType())
		{
			cType = (XSComplexType)el.getType();
			if(map.get(el.getName()) == null)
			{
				st = new Struct(el.getName());
				map.put(el.getName(), st);

				parent = cType;
				while(parent != sset.getAnyType())
				{
					if(parent.isComplexType())
					{
						write(st, parent.asComplexType(), true);
						parent = parent.getBaseType();
					}
				}
				
				processInheritance(st, cType, sset);
				st.setParent(cType.getBaseType().getName());
			}
		}
		else if(el.getType() instanceof XSSimpleType && el.getType() != sset.getAnySimpleType())
		{
			xs = el.getType().asSimpleType();
			if(xs.isRestriction())
			{
				createEnum(xs.getName(), xs.asRestriction());
			}
		}
	}

	private void write(Struct st, XSComplexType type, boolean goingup)
	{
		XSParticle							particle;
		Iterator<? extends XSAttributeUse>	it;
		XSAttributeUse						att;
		XSAttributeDecl						decl;
		Iterator<? extends XSAttGroupDecl>	itt;
		
		particle = type.getContentType().asParticle();
		if(particle != null)
		{
			write(st, particle.getTerm(), true);
		}
		
		itt = type.getAttGroups().iterator();
		while(itt.hasNext())
		{
			write(st, itt.next(), true);
		}
		
		it = type.getAttributeUses().iterator();
		while(it.hasNext())
		{
			att = it.next();
			decl = att.getDecl();
			write(st, decl, goingup && att.isRequired());
		}
	}

	private void write(Struct st, XSAttributeDecl decl, boolean goingup)
	{
		if(decl.getType().isRestriction() && decl.getType().getName() != null && !basicTypes.contains(decl.getType().getName()))
		{
			createEnum(decl.getType().getName(), decl.getType().asRestriction());
			st.addField(decl.getName(), decl.getType().getName(),
				goingup, false, decl.getFixedValue(), xsdMapping);
		}
		else if(decl.getType().isList())
		{
			st.addField(decl.getName(), decl.getType().asList().getItemType().getName(),
				goingup, true, null, xsdMapping);
		}
		else
		{
			st.addField(decl.getName(), decl.getType().getName(),
				goingup, false, decl.getFixedValue(), xsdMapping);
		}
	}

	private void createEnum(String name, XSRestrictionSimpleType type)
	{
		Enumeration					en;
		Iterator<? extends XSFacet> it;
		
		if(!enums.containsKey(name))
		{
			type = type.asRestriction();
			en = new Enumeration(name);
			it = type.getDeclaredFacets().iterator();
			while(it.hasNext())
			{
				en.addString(it.next().getValue().value);
			}
			enums.put(name, en);
		}
	}

	private void write(Struct st, XSAttGroupDecl attGroup, boolean goingup)
	{
		Iterator<? extends XSAttributeUse>	it;
		Iterator<? extends XSAttGroupDecl>	itg;
		XSAttributeUse						att;
		XSAttributeDecl						decl;

		itg = attGroup.getAttGroups().iterator();
		while(itg.hasNext())
		{
			write(st, itg.next(), goingup);
		}
		
		it = attGroup.getDeclaredAttributeUses().iterator();
		while(it.hasNext())
		{
			att = it.next();
			decl = att.getDecl();
			if(decl.getType().getName() == null)
			{
				if(decl.getType().isRestriction())
				{
					createEnum(attGroup.getName() + "_" + decl.getName(), decl.getType().asRestriction());
					st.addField(decl.getName(), attGroup.getName() + "_" + decl.getName(),
						goingup, false, decl.getFixedValue(), xsdMapping);
				}
			}
			else
			{
				write(st, decl, true);
			}
		}
	}

	private void processInheritance(Struct st, XSComplexType cType, XSSchemaSet sset)
	{
		Iterator<XSType>	ity;
		XSType				xt;
		XSParticle			particle;
		
		ity = sset.iterateTypes();
		while(ity.hasNext())
		{
			xt = ity.next();
			if(xt.getBaseType() == cType)
			{
				particle = xt.asComplexType().getContentType().asParticle();
				if(particle != null)
				{
					write(st, particle.getTerm(), false);
				}
				
				processInheritance(st, xt.asComplexType(), sset);
			}
		}
	}

	private void write(Struct st, XSTerm term, boolean goingup)
	{
		Struct			nested;
		XSModelGroup	modelGroup;
		XSParticle[]	ps;
		XSParticle		p;
		
		if(term != null && term.isModelGroup())
		{
			modelGroup = term.asModelGroup();
			ps = modelGroup.getChildren();
			for(int i = 0;i<ps.length;i++)
			{
				p = ps[i];
				term = p.getTerm();
				if(term.isModelGroup())
				{
					write(st, term, goingup);
				}
				else if(term.isElementDecl())
				{
					if(term.asElementDecl().getType().getName() == null)
					{
						nested = createNestedType(term.asElementDecl().getName(), term.asElementDecl().getType().asComplexType());
						st.addField(nested.getName(), null, goingup, p.getMaxOccurs() != 1, term.asElementDecl().getFixedValue(), xsdMapping);
					}
					else
					{
						st.addField(term.asElementDecl().getName(), term.asElementDecl().getType().getName(),
								goingup, p.getMaxOccurs() != 1, term.asElementDecl().getFixedValue(), xsdMapping);
					}
				}
			}
		}							
	}

	private Struct createNestedType(String name, XSComplexType type)
	{
		Struct		st;
		
		st = new Struct(name);
		map.put(name, st);
		
		write(st, type, true);
		
		return st;
	}

	@Override
	public void error(SAXParseException exception) throws SAXException
	{
		System.out.println(exception.getMessage());
		exception.printStackTrace();
	}

	@Override
	public void fatalError(SAXParseException exception) throws SAXException
	{
		System.out.println(exception.getMessage());
		exception.printStackTrace();
	}

	@Override
	public void warning(SAXParseException exception) throws SAXException
	{
		System.out.println(exception.getMessage());
		exception.printStackTrace();
	}

	public void addMarshaller(IMarshaller marshaller)
	{
		this.marshaller = marshaller;
	}

	public void setOutputStream(FileOutputStream os)
	{
		this.os = os;
	}

	public void setPackage(String namespace)
	{
		this.namespace = namespace;
	}
}
