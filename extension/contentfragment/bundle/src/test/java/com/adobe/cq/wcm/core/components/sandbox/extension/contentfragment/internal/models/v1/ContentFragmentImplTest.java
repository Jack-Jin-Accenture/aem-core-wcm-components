/*~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
 ~ Copyright 2017 Adobe Systems Incorporated
 ~
 ~ Licensed under the Apache License, Version 2.0 (the "License");
 ~ you may not use this file except in compliance with the License.
 ~ You may obtain a copy of the License at
 ~
 ~     http://www.apache.org/licenses/LICENSE-2.0
 ~
 ~ Unless required by applicable law or agreed to in writing, software
 ~ distributed under the License is distributed on an "AS IS" BASIS,
 ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ~ See the License for the specific language governing permissions and
 ~ limitations under the License.
 ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~*/
package com.adobe.cq.wcm.core.components.sandbox.extension.contentfragment.internal.models.v1;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;
import javax.json.Json;
import javax.json.JsonReader;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.apache.sling.api.scripting.SlingBindings;
import org.apache.sling.testing.mock.sling.servlet.MockSlingHttpServletRequest;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentVariation;
import com.adobe.cq.dam.cfm.DataType;
import com.adobe.cq.dam.cfm.FragmentData;
import com.adobe.cq.dam.cfm.FragmentTemplate;
import com.adobe.cq.dam.cfm.VariationDef;
import com.adobe.cq.export.json.ComponentExporter;
import com.adobe.cq.sightly.WCMBindings;
import com.adobe.cq.wcm.core.components.context.CoreComponentTestContext;
import com.adobe.cq.wcm.core.components.sandbox.extension.contentfragment.models.ContentFragment;
import com.day.cq.commons.jcr.JcrConstants;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.wcm.testing.mock.aem.junit.AemContext;

import static com.day.cq.commons.jcr.JcrConstants.JCR_CONTENT;
import static com.day.cq.commons.jcr.JcrConstants.JCR_DATA;
import static com.day.cq.commons.jcr.JcrConstants.JCR_DESCRIPTION;
import static com.day.cq.commons.jcr.JcrConstants.JCR_MIMETYPE;
import static com.day.cq.commons.jcr.JcrConstants.JCR_TITLE;
import static com.day.cq.dam.api.DamConstants.NT_DAM_ASSET;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ContentFragmentImplTest {

    private Logger cfmLogger;

    private static final String TEST_PAGE_PATH = "/content/contentfragments";
    private static final String TEST_CONTAINER_PATH = TEST_PAGE_PATH + "/jcr:content/root/responsivegrid";

    /* names of the content fragment component instances to test */

    private static final String CF_TEXT_ONLY_NO_PATH                 = "text-only-no-path";
    private static final String CF_TEXT_ONLY_NON_EXISTING_PATH       = "text-only-non-existing-path";
    private static final String CF_TEXT_ONLY_INVALID_PATH            = "text-only-invalid-path";
    private static final String CF_TEXT_ONLY                         = "text-only";
    private static final String CF_TEXT_ONLY_VARIATION               = "text-only-variation";
    private static final String CF_TEXT_ONLY_NON_EXISTING_VARIATION  = "text-only-non-existing-variation";
    private static final String CF_TEXT_ONLY_SINGLE_ELEMENT          = "text-only-single-element";
    private static final String CF_TEXT_ONLY_MULTIPLE_ELEMENTS       = "text-only-multiple-elements";
    private static final String CF_STRUCTURED_NO_PATH                = "structured-no-path";
    private static final String CF_STRUCTURED_NON_EXISTING_PATH      = "structured-non-existing-path";
    private static final String CF_STRUCTURED_INVALID_PATH           = "structured-invalid-path";
    private static final String CF_STRUCTURED                        = "structured";
    private static final String CF_STRUCTURED_VARIATION              = "structured-variation";
    private static final String CF_STRUCTURED_NON_EXISTING_VARIATION = "structured-non-existing-variation";
    private static final String CF_STRUCTURED_NESTED_MODEL           = "structured-nested-model";
    private static final String CF_STRUCTURED_SINGLE_ELEMENT         = "structured-single-element";
    private static final String CF_STRUCTURED_MULTIPLE_ELEMENTS      = "structured-multiple-elements";

    /* contents of the text-only and structured content fragments referenced by the above components */

    private static final String TITLE = "Test Content Fragment";
    private static final String DESCRIPTION = "This is a test content fragment.";
    private static final String TEXT_ONLY_TYPE = "/content/dam/contentfragments/text-only/jcr:content/model";
    private static final String STRUCTURED_TYPE = "global/models/test";
    private static final String STRUCTURED_TYPE_NESTED = "global/nested/models/test";
    private static final Element MAIN = new Element("main", "Main", "text/html", "<p>Main content</p>");
    private static final Element SECOND_TEXT_ONLY = new Element("second", "Second", "text/plain", "Second content");
    private static final Element SECOND_STRUCTURED = new Element("second", "Second", null, new String[]{"one", "two", "three"});
    private static final String VARIATION_NAME = "teaser";
    static {
        MAIN.addVariation(VARIATION_NAME, "Teaser", "text/html", "<p>Main content (teaser)</p>");
        SECOND_TEXT_ONLY.addVariation(VARIATION_NAME, "Teaser", "text/plain", "Second content (teaser)");
        SECOND_STRUCTURED.addVariation(VARIATION_NAME, "Teaser", null, new String[]{"one (teaser)", "two (teaser)", "three (teaser)"});
    }


    @ClassRule
    public static final AemContext AEM_CONTEXT = CoreComponentTestContext.createContext("/contentfragment", "/content");

    @BeforeClass
    public static void setUp() throws Exception {
        // load the test content fragment model into a top-level and a nested configuration
        AEM_CONTEXT.load().json("/contentfragment/test-content-conf.json", "/conf/global/settings/dam/cfm/models");
        AEM_CONTEXT.load().json("/contentfragment/test-content-conf.json", "/conf/global/nested/settings/dam/cfm/models");

        // load the content fragments
        AEM_CONTEXT.load().json("/contentfragment/test-content-dam.json", "/content/dam/contentfragments");

        // set content element values for the text-only fragment (stored as binary properties)
        String path = "/content/dam/contentfragments/text-only/";
        Element.Variation mainVariation = MAIN.variations.get(VARIATION_NAME);
        Element.Variation secondVariation = SECOND_TEXT_ONLY.variations.get(VARIATION_NAME);
        AEM_CONTEXT.load().binaryFile(new ByteArrayInputStream(MAIN.values[0].getBytes(UTF_8)),
                path + "jcr:content/renditions/original", MAIN.contentType);
        AEM_CONTEXT.load().binaryFile(new ByteArrayInputStream(mainVariation.values[0].getBytes(UTF_8)),
                path + "jcr:content/renditions/" + VARIATION_NAME, mainVariation.contentType);
        AEM_CONTEXT.load().binaryFile(new ByteArrayInputStream(SECOND_TEXT_ONLY.values[0].getBytes(UTF_8)),
                path + "subassets/second/jcr:content/renditions/original", SECOND_TEXT_ONLY.contentType);
        AEM_CONTEXT.load().binaryFile(new ByteArrayInputStream(secondVariation.values[0].getBytes(UTF_8)),
                path + "subassets/second/jcr:content/renditions/" + VARIATION_NAME, secondVariation.contentType);

        // register an adapter that adapts resources to mocks of content fragments
        AEM_CONTEXT.registerAdapter(Resource.class, com.adobe.cq.dam.cfm.ContentFragment.class, ADAPTER);
    }

    @Before
    public void setTestFixture() throws NoSuchFieldException, IllegalAccessException {
        cfmLogger = spy(LoggerFactory.getLogger("FakeLogger"));
        Field field = ContentFragmentImpl.class.getDeclaredField("LOG");
        Field modifiersField = Field.class.getDeclaredField("modifiers");
        field.setAccessible(true);
        // remove final modifier from field

        modifiersField.setAccessible(true);
        modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
        field.set(null, cfmLogger);
    }

    @Test
    public void testTextOnlyNoPath() {
        ContentFragment fragment = getTestContentFragment(CF_TEXT_ONLY_NO_PATH);
        verify(cfmLogger).warn("Please provide a path for the content fragment component.");
        assertNotNull("Model shouldn't be null when no path is set", fragment);
    }

    @Test
    public void testTextOnlyNonExistingPath() {
        ContentFragment fragment = getTestContentFragment(CF_TEXT_ONLY_NON_EXISTING_PATH);
        verify(cfmLogger).error("Content Fragment can not be initialized because the '{}' does not exist.", "/content/dam/contentfragments/non-existing");
        assertNotNull("Model shouldn't be null when the path does not exist", fragment);
    }

    @Test
    public void testTextOnlyInvalidPath() {
        ContentFragment fragment = getTestContentFragment(CF_TEXT_ONLY_INVALID_PATH);
        verify(cfmLogger).error("Content Fragment can not be initialized because '{}' is not a content fragment.", "/content/dam/contentfragments");
        assertNotNull("Model shouldn't be null when the path is not a content fragment", fragment);
    }

    @Test
    public void testTextOnly() {
        ContentFragment fragment = getTestContentFragment(CF_TEXT_ONLY);
        assertContentFragment(fragment, TITLE, DESCRIPTION, TEXT_ONLY_TYPE, MAIN, SECOND_TEXT_ONLY);
    }

    @Test
    public void testTextOnlyVariation() {
        ContentFragment fragment = getTestContentFragment(CF_TEXT_ONLY_VARIATION);
        assertContentFragment(fragment, VARIATION_NAME, TITLE, DESCRIPTION, TEXT_ONLY_TYPE, MAIN, SECOND_TEXT_ONLY);
    }

    @Test
    public void testTextOnlyNonExistingVariation() {
        ContentFragment fragment = getTestContentFragment(CF_TEXT_ONLY_NON_EXISTING_VARIATION);
        assertContentFragment(fragment, TITLE, DESCRIPTION, TEXT_ONLY_TYPE, MAIN, SECOND_TEXT_ONLY);
    }

    @Test
    public void testTextOnlySingleElement() {
        ContentFragment fragment = getTestContentFragment(CF_TEXT_ONLY_SINGLE_ELEMENT);
        assertContentFragment(fragment, TITLE, DESCRIPTION, TEXT_ONLY_TYPE, SECOND_TEXT_ONLY);
    }

    @Test
    public void testTextOnlyMultipleElements() {
        ContentFragment fragment = getTestContentFragment(CF_TEXT_ONLY_MULTIPLE_ELEMENTS);
        assertContentFragment(fragment, TITLE, DESCRIPTION, TEXT_ONLY_TYPE, SECOND_TEXT_ONLY, MAIN);
    }

    @Test
    public void testStructuredNoPath() {
        ContentFragment fragment = getTestContentFragment(CF_STRUCTURED_NO_PATH);
        assertNotNull("Model shouldn't be null when no path is set", fragment);
    }

    @Test
    public void testStructuredNonExistingPath() {
        ContentFragment fragment = getTestContentFragment(CF_STRUCTURED_NON_EXISTING_PATH);
        assertNotNull("Model shouldn't be null when the path does not exist", fragment);
    }

    @Test
    public void testStructuredOnlyInvalidPath() {
        ContentFragment fragment = getTestContentFragment(CF_STRUCTURED_INVALID_PATH);
        assertNotNull("Model shouldn't be null when the path is not a content fragment", fragment);
    }

    @Test
    public void testStructured() {
        ContentFragment fragment = getTestContentFragment(CF_STRUCTURED);
        assertContentFragment(fragment, TITLE, DESCRIPTION, STRUCTURED_TYPE, MAIN, SECOND_STRUCTURED);
    }

    @Test
    public void testStructuredVariation() {
        ContentFragment fragment = getTestContentFragment(CF_STRUCTURED_VARIATION);
        assertContentFragment(fragment, VARIATION_NAME, TITLE, DESCRIPTION, STRUCTURED_TYPE, MAIN, SECOND_STRUCTURED);
    }

    @Test
    public void testStructuredNonExistingVariation() {
        ContentFragment fragment = getTestContentFragment(CF_STRUCTURED_NON_EXISTING_VARIATION);
        assertContentFragment(fragment, TITLE, DESCRIPTION, STRUCTURED_TYPE, MAIN, SECOND_STRUCTURED);
    }

    @Test
    public void testStructuredNestedModel() {
        ContentFragment fragment = getTestContentFragment(CF_STRUCTURED_NESTED_MODEL);
        assertContentFragment(fragment, TITLE, DESCRIPTION, STRUCTURED_TYPE_NESTED, MAIN, SECOND_STRUCTURED);
    }

    @Test
    public void testStructuredSingleElement() {
        ContentFragment fragment = getTestContentFragment(CF_STRUCTURED_SINGLE_ELEMENT);
        assertContentFragment(fragment, TITLE, DESCRIPTION, STRUCTURED_TYPE, SECOND_STRUCTURED);
    }

    @Test
    public void testStructuredMultipleElements() {
        ContentFragment fragment = getTestContentFragment(CF_STRUCTURED_MULTIPLE_ELEMENTS);
        assertContentFragment(fragment, TITLE, DESCRIPTION, STRUCTURED_TYPE, SECOND_STRUCTURED, MAIN);
    }

    @Test
    public void testGetExportedType() {
        ContentFragmentImpl fragment = (ContentFragmentImpl) getTestContentFragment(CF_TEXT_ONLY);
        assertEquals(ContentFragmentImpl.RESOURCE_TYPE, fragment.getExportedType());
    }

    @Test
    public void testGetExportedItems() {
        ContentFragmentImpl fragment = (ContentFragmentImpl) getTestContentFragment(CF_TEXT_ONLY);
        final Map<String, ComponentExporter> exportedItems = fragment.getExportedItems();
        assertEquals(2, exportedItems.size());
        assertEquals(true, exportedItems.containsKey("main"));
        assertEquals(true, exportedItems.containsKey("second"));
    }

    @Test
    public void testGetExportedElementType() {
        ContentFragmentImpl fragment = (ContentFragmentImpl) getTestContentFragment(CF_TEXT_ONLY);
        final Map<String, ComponentExporter> exportedItems = fragment.getExportedItems();
        final ComponentExporter mainElement = exportedItems.get("main");
        assertEquals("text/html", mainElement.getExportedType());
    }
    
    @Test
    public void testJSONExport() throws IOException {
        ContentFragmentImpl fragment = (ContentFragmentImpl) getTestContentFragment(CF_TEXT_ONLY);
        Writer writer = new StringWriter();
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithView(ContentFragmentImpl.class).writeValue(writer, fragment);
        JsonReader jsonReaderOutput = Json.createReader(IOUtils.toInputStream(writer.toString()));
        JsonReader jsonReaderExpected = Json.createReader(Thread.currentThread().getContextClassLoader().getClass()
                .getResourceAsStream("/contentfragment/test-expected-content-export.json"));
        assertEquals(jsonReaderExpected.read(), jsonReaderOutput.read());
    }

    /* helper methods */

    /**
     * Adapts the specified content fragment component to the {@link ContentFragment} Sling Model and returns it.
     */
    private ContentFragment getTestContentFragment(String name) {
        String path = TEST_CONTAINER_PATH + "/" + name;
        ResourceResolver resolver = AEM_CONTEXT.resourceResolver();
        Resource resource = resolver.getResource(path);
        MockSlingHttpServletRequest request = new MockSlingHttpServletRequest(resolver, AEM_CONTEXT.bundleContext());
        request.setResource(resource);
        SlingBindings slingBindings = new SlingBindings();
        slingBindings.put(SlingBindings.RESOLVER, resolver);
        slingBindings.put(WCMBindings.PROPERTIES, resource.adaptTo(ValueMap.class));
        request.setAttribute(SlingBindings.class.getName(), slingBindings);
        return request.adaptTo(ContentFragment.class);
    }

    /**
     * Asserts that the content of the specified {@code fragment} corresponds to the expected values using the
     * default variation.
     */
    private void assertContentFragment(ContentFragment fragment, String expectedTitle, String expectedDescription,
                                       String expectedType, Element... expectedElements) {
        assertContentFragment(fragment, null, expectedTitle, expectedDescription, expectedType, expectedElements);
    }

    /**
     * Asserts that the content of the specified {@code fragment} corresponds to the expected values using the
     * specified variation.
     */
    private void assertContentFragment(ContentFragment fragment, String variationName, String expectedTitle,
                                       String expectedDescription, String expectedType, Element... expectedElements) {
        assertEquals("Content fragment has wrong title", expectedTitle, fragment.getTitle());
        assertEquals("Content fragment has wrong description", expectedDescription  ,fragment.getDescription());
        assertEquals("Content fragment has wrong type", expectedType ,fragment.getType());
        List<ContentFragment.Element> elements = fragment.getElements();
        assertEquals("Content fragment has wrong number of elements", expectedElements.length, elements.size());
        for (int i = 0; i < expectedElements.length; i++) {
            ContentFragment.Element element = elements.get(i);
            Element expected = expectedElements[i];
            assertEquals("Element has wrong name", expected.name, element.getName());
            assertEquals("Element has wrong title", expected.title, element.getTitle());
            assertEquals("Element has wrong multi-valued flag", expected.isMultiValued, element.isMultiValued());
            String contentType = expected.contentType;
            String displayValue = StringUtils.join(expected.values, ", ");
            String[] displayValues = expected.values;
            if (StringUtils.isNotEmpty(variationName)) {
                contentType = expected.variations.get(variationName).contentType;
                displayValue = StringUtils.join(expected.variations.get(variationName).values, ", ");
                displayValues = expected.variations.get(variationName).values;
            }
            assertEquals("Element has wrong content type", contentType, element.getContentType());
            assertEquals("Element has wrong display value", displayValue, element.getDisplayValue());
            assertArrayEquals("Element has wrong display values", displayValues, element.getDisplayValues());
        }
    }

    /**
     * Adapts resources to {@link com.adobe.cq.dam.cfm.ContentFragment} objects by mocking parts of their API.
     */
    public static final com.google.common.base.Function<Resource, com.adobe.cq.dam.cfm.ContentFragment> ADAPTER =
            new com.google.common.base.Function<Resource, com.adobe.cq.dam.cfm.ContentFragment>() {

        private final String PATH_DATA = JCR_CONTENT + "/data";
        private final String PATH_MASTER = PATH_DATA + "/master";
        private final String PATH_MODEL = JCR_CONTENT + "/model";
        private final String PATH_MODEL_ELEMENTS = PATH_MODEL + "/elements";
        private final String PATH_MODEL_VARIATIONS = PATH_MODEL + "/variations";
        private final String PATH_MODEL_DIALOG_ITEMS = JCR_CONTENT + "/model/cq:dialog/content/items";
        private final String PN_CONTENT_FRAGMENT = "contentFragment";
        private final String PN_MODEL = "cq:model";
        private final String PN_ELEMENT_NAME = "name";
        private final String PN_ELEMENT_TITLE = "fieldLabel";
        private final String PN_VALUE_TYPE = "valueType";
        private final String MAIN_ELEMENT = "main";

        @Nullable
        @Override
        public com.adobe.cq.dam.cfm.ContentFragment apply(@Nullable Resource resource) {
            // check if the resource is valid and an asset
            if (resource == null || !resource.isResourceType(NT_DAM_ASSET)) {
                return null;
            }

            // check if the resource is a content fragment
            Resource content = resource.getChild(JCR_CONTENT);
            ValueMap contentProperties = content.getValueMap();
            if (!contentProperties.get(PN_CONTENT_FRAGMENT, Boolean.FALSE)) {
                return null;
            }

            // check if the content fragment is text-only or structured
            Resource data = resource.getChild(PATH_DATA);
            boolean isStructured = data != null;

            /* get content fragment properties, model and elements */

            String title = contentProperties.get(JCR_TITLE, String.class);
            String description = contentProperties.get(JCR_DESCRIPTION, String.class);
            Resource model;
            Resource modelAdaptee;
            List<ContentElement> elements = new LinkedList<>();

            if (isStructured) {
                // get the model (referenced in the property)
                model = resource.getResourceResolver().getResource(data.getValueMap().get(PN_MODEL, String.class));
                // for the 'adaptTo' mock below we use the jcr:content child to mimick the real behavior
                modelAdaptee = model.getChild(JCR_CONTENT);
                // create an element mock for each property on the master node
                Resource master = resource.getChild(PATH_MASTER);
                for (String name : master.getValueMap().keySet()) {
                    // skip the primary type and content type properties
                    if (JcrConstants.JCR_PRIMARYTYPE.equals(name) || name.endsWith("@ContentType")) {
                        continue;
                    }
                    elements.add(getMockElement(resource, name, model));
                }
            } else {
                // get the model (stored in the fragment itself)
                model = resource.getChild(PATH_MODEL);
                modelAdaptee = model;
                // add the "main" element to the list
                elements.add(getMockElement(resource, null, null));
                // create an element mock for each subasset
                Resource subassets = resource.getChild("subassets");
                if (subassets != null) {
                    for (Resource subasset : subassets.getChildren()) {
                        elements.add(getMockElement(resource, subasset.getName(), null));
                    }
                }
            }

            /* create mock objects */

            com.adobe.cq.dam.cfm.ContentFragment fragment = mock(com.adobe.cq.dam.cfm.ContentFragment.class);
            when(fragment.getTitle()).thenReturn(title);
            when(fragment.getDescription()).thenReturn(description);
            when(fragment.adaptTo(Resource.class)).thenReturn(resource);
            when(fragment.getElement(any(String.class))).thenAnswer(invocation -> {
                String name = invocation.getArgumentAt(0, String.class);
                return getMockElement(resource, name, isStructured ? model : null);
            });
            when(fragment.hasElement(any(String.class))).thenAnswer(invocation -> {
                String name = invocation.getArgumentAt(0, String.class);
                return fragment.getElement(name) != null;
            });
            when(fragment.getElements()).thenReturn(elements.iterator());

            List<VariationDef> variations = new LinkedList<>();
            ContentElement main = fragment.getElement(null);
            Iterator<ContentVariation> iterator = main.getVariations();
            while (iterator.hasNext()) {
                ContentVariation variation = iterator.next();
                variations.add(new VariationDef() {
                    @Override
                    public String getName() {
                        return variation.getName();
                    }

                    @Override
                    public String getTitle() {
                        return variation.getTitle();
                    }

                    @Override
                    public String getDescription() {
                        return variation.getDescription();
                    }
                });
            }
            when(fragment.listAllVariations()).thenReturn(variations.iterator());

            FragmentTemplate template = mock(FragmentTemplate.class);
            when(template.adaptTo(Resource.class)).thenReturn(modelAdaptee);
            when(fragment.getTemplate()).thenReturn(template);

            return fragment;
        }

        /**
         * Creates a mock of a content element for a text-only (if {@code model} is {@code null}) or structured
         * (if {@code model} is not {@code null}) content fragment.
         */
        private ContentElement getMockElement(Resource resource, String name, Resource model) {
            // get the respective element
            Element element;
            if (model == null) {
                element = getTextOnlyElement(resource, name);
            } else {
                element = getStructuredElement(resource, model, name);
            }
            if (element == null) {
                return null;
            }

            /* create mock objects */

            // mock data type
            DataType dataType = mock(DataType.class);
            when(dataType.isMultiValue()).thenReturn(element.isMultiValued);

            // mock fragment data
            FragmentData data = mock(FragmentData.class);
            when(data.getValue()).thenReturn(element.isMultiValued ? element.values : element.values[0]);
            when(data.getValue(String.class)).thenReturn(element.values[0]);
            when(data.getValue(String[].class)).thenReturn(element.values);
            when(data.getContentType()).thenReturn(element.contentType);
            when(data.getDataType()).thenReturn(dataType);

            // mock content element
            ContentElement contentElement = mock(ContentElement.class);
            when(contentElement.getName()).thenReturn(element.name);
            when(contentElement.getTitle()).thenReturn(element.title);
            when(contentElement.getContent()).thenReturn(element.values[0]);
            when(contentElement.getContentType()).thenReturn(element.contentType);
            when(contentElement.getValue()).thenReturn(data);

            // mock variations
            Map<String, ContentVariation> variations = new LinkedHashMap<>();
            for (Element.Variation variation : element.variations.values()) {
                FragmentData variationData = mock(FragmentData.class);
                when(variationData.getValue()).thenReturn(element.isMultiValued ? variation.values : variation.values[0]);
                when(variationData.getValue(String.class)).thenReturn(variation.values[0]);
                when(variationData.getValue(String[].class)).thenReturn(variation.values);
                when(variationData.getContentType()).thenReturn(variation.contentType);
                when(variationData.getDataType()).thenReturn(dataType);

                ContentVariation contentVariation = mock(ContentVariation.class);
                when(contentVariation.getName()).thenReturn(variation.name);
                when(contentVariation.getTitle()).thenReturn(variation.title);
                when(contentVariation.getContent()).thenReturn(variation.values[0]);
                when(contentVariation.getContentType()).thenReturn(variation.contentType);
                when(contentVariation.getValue()).thenReturn(variationData);
                variations.put(variation.name, contentVariation);
            }
            when(contentElement.getVariations()).thenReturn(variations.values().iterator());
            when(contentElement.getVariation(any(String.class))).thenAnswer(invocation -> {
                String variationName = invocation.getArgumentAt(0, String.class);
                return variations.get(variationName);
            });

            return contentElement;
        }

        /**
         * Collects and returns the information of a content element for text-only content fragment.
         */
        private Element getTextOnlyElement(Resource resource, String name) {
            Element element = new Element();
            // text-only elements are never multi-valued
            element.isMultiValued = false;
            // if the name is null we use the main element
            element.name = name == null ? MAIN_ELEMENT : name;

            // loop through element definitions in the model and find the matching one
            boolean found = false;
            Resource elements = resource.getChild(PATH_MODEL_ELEMENTS);
            for (Resource elementResource : elements.getChildren()) {
                ValueMap properties = elementResource.getValueMap();
                if (element.name.equals(properties.get(PN_ELEMENT_NAME))) {
                    // set the element title
                    element.title = properties.get(JCR_TITLE, String.class);
                    found = true;
                    break;
                }
            }
            // return if we didn't find an element with the given name
            if (!found) {
                return null;
            }

            try {
                // get path to the asset resource (main element or correct subasset)
                String path = MAIN_ELEMENT.equals(element.name) ? "" : "subassets/" + element.name + "/";
                Resource renditions = resource.getChild(path + JCR_CONTENT + "/renditions");
                // loop over the renditions (i.e. variations)
                for (Resource rendition : renditions.getChildren()) {
                    // get content and content type
                    ValueMap properties = rendition.getChild(JCR_CONTENT).getValueMap();
                    String content = IOUtils.toString(properties.get(JCR_DATA, InputStream.class), UTF_8);
                    String contentType = properties.get(JCR_MIMETYPE, String.class);

                    // get variation definition from model
                    Resource variation = resource.getChild(PATH_MODEL_VARIATIONS + "/" + rendition.getName());
                    if (variation != null) {
                        String title = variation.getValueMap().get(JCR_TITLE, String.class);
                        element.addVariation(rendition.getName(), title, contentType, new String[]{ content });
                    } else {
                        element.values = new String[]{ content };
                        element.contentType = contentType;
                    }
                }
            } catch (IOException e) {
                return null;
            }

            return element;
        }

        /**
         * Collects and returns the information of a content element for structured content fragment.
         */
        private Element getStructuredElement(Resource resource, Resource model, String name) {
            Element element = new Element();
            element.name = name;

            // loop through element definitions in the model and find the matching one (or first one, if name is null)
            boolean found = false;
            Resource items = model.getChild(PATH_MODEL_DIALOG_ITEMS);
            for (Resource item : items.getChildren()) {
                ValueMap properties = item.getValueMap();
                String elementName = properties.get(PN_ELEMENT_NAME, String.class);
                if (element.name == null || element.name.equals(elementName)) {
                    // set the element name (in case it was null)
                    element.name = elementName;
                    // set the element title
                    element.title = properties.get(PN_ELEMENT_TITLE, String.class);
                    // determine if the element is multi-valued (if the value type is e.g. "string[]")
                    element.isMultiValued = properties.get(PN_VALUE_TYPE, "").endsWith("[]");
                    found = true;
                    break;
                }
            }
            // return if we didn't find an element with the given name
            if (!found) {
                return null;
            }

            // loop over the data nodes
            for (Resource data : resource.getChild(PATH_DATA).getChildren()) {
                ValueMap properties = data.getValueMap();
                String[] values = properties.get(element.name, String[].class);
                String contentType = properties.get(element.name + "@ContentType", String.class);
                if ("master".equals(data.getName())) {
                    element.values = values;
                    element.contentType = contentType;
                } else {
                    properties = resource.getChild(PATH_MODEL_VARIATIONS + "/" + data.getName()).getValueMap();
                    String title = properties.get(JCR_TITLE, String.class);
                    element.addVariation(data.getName(), title, contentType, values);
                }
            }

            return element;
        }

    };

    /**
     * Helper class to represent an element and its variations, used to model expected values and to mock objects.
     */
    private static class Element {

        private static class Variation {

            String name;
            String title;
            String contentType;
            String[] values;

            Variation(String name, String title, String contentType, String[] values) {
                this.name = name;
                this.title = title;
                this.contentType = contentType;
                this.values = values;
            }

            Variation(String name, String title, String contentType, String value) {
                this(name, title, contentType, new String[]{ value });
            }

        }

        String name;
        String title;
        boolean isMultiValued;
        String contentType;
        String[] values;
        Map<String, Variation> variations = new LinkedHashMap<>();

        Element() {
        }

        Element(String name, String title, String contentType, String value) {
            this(name, title, contentType, new String[]{value});
            this.isMultiValued = false;
        }

        Element(String name, String title, String contentType, String[] values) {
            this.name = name;
            this.title = title;
            this.contentType = contentType;
            this.isMultiValued = true;
            this.values = values;
        }

        private void addVariation(String name, String title, String contentType, String[] values) {
            variations.put(name, new Variation(name, title, contentType, values));
        }

        private void addVariation(String name, String title, String contentType, String value) {
            variations.put(name, new Variation(name, title, contentType, value));
        }

    }

}