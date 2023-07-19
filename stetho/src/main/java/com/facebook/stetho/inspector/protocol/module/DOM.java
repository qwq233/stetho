/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.stetho.inspector.protocol.module;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;

import com.facebook.stetho.common.Accumulator;
import com.facebook.stetho.common.ArrayListAccumulator;
import com.facebook.stetho.common.LogUtil;
import com.facebook.stetho.common.UncheckedCallable;
import com.facebook.stetho.common.Util;
import com.facebook.stetho.inspector.DomainContext;
import com.facebook.stetho.inspector.elements.Document;
import com.facebook.stetho.inspector.elements.DocumentView;
import com.facebook.stetho.inspector.elements.ElementInfo;
import com.facebook.stetho.inspector.elements.NodeDescriptor;
import com.facebook.stetho.inspector.elements.NodeType;
import com.facebook.stetho.inspector.elements.android.ActivityTracker;
import com.facebook.stetho.inspector.helper.ChromePeerManager;
import com.facebook.stetho.inspector.helper.PeersRegisteredListener;
import com.facebook.stetho.inspector.jsonrpc.JsonRpcException;
import com.facebook.stetho.inspector.jsonrpc.JsonRpcPeer;
import com.facebook.stetho.inspector.jsonrpc.JsonRpcResult;
import com.facebook.stetho.inspector.jsonrpc.protocol.JsonRpcError;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsDomain;
import com.facebook.stetho.inspector.protocol.ChromeDevtoolsMethod;
import com.facebook.stetho.json.ObjectMapper;
import com.facebook.stetho.json.annotation.JsonProperty;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nullable;

public class DOM implements ChromeDevtoolsDomain {
  private final ObjectMapper mObjectMapper;
  private final Document mDocument;
  private final Map<String, List<Integer>> mSearchResults;
  private final AtomicInteger mResultCounter;
  private final ChromePeerManager mPeerManager;
  private final DocumentUpdateListener mListener;

  private ChildNodeRemovedEvent mCachedChildNodeRemovedEvent;
  private ChildNodeInsertedEvent mCachedChildNodeInsertedEvent;

  private DomainContext mDomainContext;

  public DOM(Document document) {
    mObjectMapper = new ObjectMapper();
    mDocument = Util.throwIfNull(document);
    mSearchResults = Collections.synchronizedMap(
      new HashMap<String, List<Integer>>());
    mResultCounter = new AtomicInteger(0);
    mPeerManager = new ChromePeerManager();
    mPeerManager.setListener(new PeerManagerListener());
    mListener = new DocumentUpdateListener();
  }

  @Override
  public void onAttachContext(DomainContext domainContext) {
    mDomainContext = domainContext;
  }

  @ChromeDevtoolsMethod
  public void enable(JsonRpcPeer peer, JSONObject params) {
    mPeerManager.addPeer(peer);
  }

  @ChromeDevtoolsMethod
  public void disable(JsonRpcPeer peer, JSONObject params) {
    mPeerManager.removePeer(peer);
  }

  @ChromeDevtoolsMethod
  public JsonRpcResult getDocument(JsonRpcPeer peer, JSONObject params) {
    final GetDocumentResponse result = new GetDocumentResponse();

    result.root = mDocument.postAndWait(new UncheckedCallable<Node>() {
      @Override
      public Node call() {
        Object element = mDocument.getRootElement();
        return createNodeForElement(element, mDocument.getDocumentView(), null);
      }
    });

    return result;
  }

  @ChromeDevtoolsMethod
  public void setInspectedNode(JsonRpcPeer peer, JSONObject params) {
    final SetInspectedNodeRequest request = mObjectMapper.convertValue(
            params,
            SetInspectedNodeRequest.class
    );
    mDocument.postAndWait(() -> {
      Object o = mDocument.getElementForNodeId(request.nodeId);
      mDomainContext.setInspectedObject(o);
    });
  }

  @ChromeDevtoolsMethod
  public PushNodesByBackendIdsToFrontendResponse pushNodesByBackendIdsToFrontend(JsonRpcPeer peer, JSONObject params) {
    final PushNodesByBackendIdsToFrontendRequest request = mObjectMapper.convertValue(
            params,
            PushNodesByBackendIdsToFrontendRequest.class
    );
    final PushNodesByBackendIdsToFrontendResponse response = new PushNodesByBackendIdsToFrontendResponse();
    response.nodeIds = request.backendNodeIds;
    return response;
  }

  @ChromeDevtoolsMethod
  public JsonRpcResult getNodeForLocation(JsonRpcPeer peer, JSONObject params) {
    final GetNodeForLocationRequest request = mObjectMapper.convertValue(
            params,
            GetNodeForLocationRequest.class
    );
    final GetNodeForLocationResponse result = new GetNodeForLocationResponse();

    result.nodeId = mDocument.postAndWait(() -> {
      Object element = mDomainContext.inspectingRoot();
      if (element == null) return 0;
      int x = (int) (request.x / mDomainContext.scaleX);
      int y = (int) (request.y / mDomainContext.scaleY);
      FindResult findResult = new FindResult();
      findNodeContainsPoint(element, mDocument.getDocumentView(), x, y, findResult);
      return findResult.id;
    });

    result.backendNodeId = result.nodeId;

    return result;
  }

  @ChromeDevtoolsMethod
  public JsonRpcResult getBoxModel(JsonRpcPeer peer, JSONObject params) {
    final GetBoxModelRequest request = mObjectMapper.convertValue(
            params,
            GetBoxModelRequest.class
    );
    final GetBoxModelResponse result = new GetBoxModelResponse();
    final BoxModel model = new BoxModel();
    result.model = model;

    mDocument.postAndWait(() -> {
      Object element = mDocument.getElementForNodeId(request.nodeId);
      if (element == null) {
        LogUtil.e("Tried to get the style of an element that does not exist, using nodeid=" +
                request.nodeId);
        return;
      }
      Double[] quad = new Double[8];
      for (int i = 0; i < 8; i++) {
        quad[i] = 0.;
      }
      mDocument.getElementComputedStyles(element, (name, value) -> {
        // (0,1) (2,3)
        // (6,7) (4,5)
        if (value == null) return;
        if ("left".equals(name)) {
          quad[0] = quad[6] = Double.parseDouble(value) * mDomainContext.scaleX;
        } else if ("right".equals(name)) {
          quad[2] = quad[4] = Double.parseDouble(value) * mDomainContext.scaleX;
        } else if ("top".equals(name)) {
          quad[1] = quad[3] = Double.parseDouble(value) * mDomainContext.scaleY;
        } else if ("bottom".equals(name)) {
          quad[5] = quad[7] = Double.parseDouble(value) * mDomainContext.scaleY;
        }
      });
      int width = (int) (quad[2] - quad[0]);
      int height = (int) (quad[5] - quad[3]);
      model.content = model.border = model.margin = model.padding = Arrays.asList(quad);
      model.width = width;
      model.height = height;
    });

    return result;
  }

  @ChromeDevtoolsMethod
  public void highlightNode(JsonRpcPeer peer, JSONObject params) {
    final HighlightNodeRequest request =
      mObjectMapper.convertValue(params, HighlightNodeRequest.class);
    if (request.nodeId == null) {
      LogUtil.w("DOM.highlightNode was not given a nodeId; JS objectId is not supported");
      return;
    }

    final RGBAColor contentColor = request.highlightConfig.contentColor;
    if (contentColor == null) {
      LogUtil.w("DOM.highlightNode was not given a color to highlight with");
      return;
    }

    mDocument.postAndWait(new Runnable() {
      @Override
      public void run() {
        Object element = mDocument.getElementForNodeId(request.nodeId);
        if (element != null) {
          mDocument.highlightElement(element, contentColor.getColor());
        }
      }
    });
  }

  @ChromeDevtoolsMethod
  public void hideHighlight(JsonRpcPeer peer, JSONObject params) {
    mDocument.postAndWait(new Runnable() {
      @Override
      public void run() {
        mDocument.hideHighlight();
      }
    });
  }

  @ChromeDevtoolsMethod
  public ResolveNodeResponse resolveNode(JsonRpcPeer peer, JSONObject params)
      throws JsonRpcException {
    final ResolveNodeRequest request = mObjectMapper.convertValue(params, ResolveNodeRequest.class);

    final Object element = mDocument.postAndWait(new UncheckedCallable<Object>() {
      @Override
      public Object call() {
        return mDocument.getElementForNodeId(request.nodeId);
      }
    });

    if (element == null) {
      throw new JsonRpcException(
          new JsonRpcError(
              JsonRpcError.ErrorCode.INVALID_PARAMS,
              "No known nodeId=" + request.nodeId,
              null /* data */));
    }

    int mappedObjectId = Runtime.mapObject(peer, element);

    Runtime.RemoteObject remoteObject = new Runtime.RemoteObject();
    remoteObject.type = Runtime.ObjectType.OBJECT;
    remoteObject.subtype = Runtime.ObjectSubType.NODE;
    remoteObject.className = element.getClass().getName();
    remoteObject.value = null; // not a primitive
    remoteObject.description = null; // not sure what this does...
    remoteObject.objectId = String.valueOf(mappedObjectId);
    ResolveNodeResponse response = new ResolveNodeResponse();
    response.object = remoteObject;

    return response;
  }

  @ChromeDevtoolsMethod
  public void setAttributesAsText(JsonRpcPeer peer, JSONObject params) {
    final SetAttributesAsTextRequest request = mObjectMapper.convertValue(
        params,
        SetAttributesAsTextRequest.class);

    mDocument.postAndWait(new Runnable() {
      @Override
      public void run() {
        Object element = mDocument.getElementForNodeId(request.nodeId);
        if (element != null) {
          mDocument.setAttributesAsText(element, request.text);
        }
      }
    });
  }

  @ChromeDevtoolsMethod
  public void setInspectModeEnabled(JsonRpcPeer peer, JSONObject params) {
    final SetInspectModeEnabledRequest request = mObjectMapper.convertValue(
        params,
        SetInspectModeEnabledRequest.class);

    mDocument.postAndWait(new Runnable() {
      @Override
      public void run() {
        mDocument.setInspectModeEnabled(request.enabled);
      }
    });
  }

  @ChromeDevtoolsMethod
  public PerformSearchResponse performSearch(JsonRpcPeer peer, final JSONObject params) {
    final PerformSearchRequest request = mObjectMapper.convertValue(
        params,
        PerformSearchRequest.class);

    final ArrayListAccumulator<Integer> resultNodeIds = new ArrayListAccumulator<>();

    mDocument.postAndWait(new Runnable() {
      @Override
      public void run() {
        mDocument.findMatchingElements(request.query, resultNodeIds);
      }
    });

    // Each search action has a unique ID so that
    // it can be queried later.
    final String searchId = String.valueOf(mResultCounter.getAndIncrement());

    mSearchResults.put(searchId, resultNodeIds);

    final PerformSearchResponse response = new PerformSearchResponse();
    response.searchId = searchId;
    response.resultCount = resultNodeIds.size();

    return response;
  }

  @ChromeDevtoolsMethod
  public GetSearchResultsResponse getSearchResults(JsonRpcPeer peer, JSONObject params) {
    final GetSearchResultsRequest request = mObjectMapper.convertValue(
        params,
        GetSearchResultsRequest.class);

    if (request.searchId == null) {
      LogUtil.w("searchId may not be null");
      return null;
    }

    final List<Integer> results = mSearchResults.get(request.searchId);

    if (results == null) {
      LogUtil.w("\"" + request.searchId + "\" is not a valid reference to a search result");
      return null;
    }

    final List<Integer> resultsRange = results.subList(request.fromIndex, request.toIndex);

    final GetSearchResultsResponse response = new GetSearchResultsResponse();
    response.nodeIds = resultsRange;

    return response;
  }

  @ChromeDevtoolsMethod
  public void discardSearchResults(JsonRpcPeer peer, JSONObject params) {
    final DiscardSearchResultsRequest request = mObjectMapper.convertValue(
      params,
      DiscardSearchResultsRequest.class);

    if (request.searchId != null) {
      mSearchResults.remove(request.searchId);
    }
  }

  private static class FindResult {
    int id = 0;
    int size = Integer.MAX_VALUE;
  }

  private void findNodeContainsPoint(
      Object element,
      DocumentView documentView,
      int x, int y, FindResult findResult) {
    if (element instanceof Activity) {
      if (!ActivityTracker.get().isActivityResumed((Activity) element)) return;
    }
    if (element instanceof View) {
      View v = (View) element;
      int width = v.getRight() - v.getLeft();
      int height = v.getBottom() - v.getTop();
      int[] point = new int[2];
      v.getLocationInWindow(point);
      // To see if this view contains the point.
      if (x >= point[0] && x <= point[0] + width && y >= point[1] && y <= point[1] + height) {
        int size = width * height;
        // we prefer the smaller one
        if (size <= findResult.size) {
          findResult.id = mDocument.getNodeIdForElement(element);
          findResult.size = size;
        }
      } else {
        // not containing, don't continue
        return;
      }
    }
    ElementInfo info = documentView.getElementInfo(element);

    List<Object> childrens = info.children;

    if (element instanceof View) {
      // traversal in reverse order
      for (int i = childrens.size() - 1; i >= 0; i--) {
        findNodeContainsPoint(childrens.get(i), documentView, x, y, findResult);
      }
    } else {
      // activities are already inserted in reverse order (see ApplicationDescriptor)
      for (Object e: childrens) {
        findNodeContainsPoint(e, documentView, x, y, findResult);
      }
    }
  }

  private Node createNodeForElement(
      Object element,
      DocumentView view,
      @Nullable Accumulator<Object> processedElements) {
    if (processedElements != null) {
      processedElements.store(element);
    }

    NodeDescriptor descriptor = mDocument.getNodeDescriptor(element);

    Node node = new DOM.Node();
    node.nodeId = node.backendNodeId = mDocument.getNodeIdForElement(element);
    node.nodeType = descriptor.getNodeType(element);
    node.nodeName = descriptor.getNodeName(element);
    node.localName = descriptor.getLocalName(element);
    node.nodeValue = descriptor.getNodeValue(element);

    Document.AttributeListAccumulator accumulator = new Document.AttributeListAccumulator();
    descriptor.getAttributes(element, accumulator);

    // Attributes
    node.attributes = accumulator;

    // Children
    ElementInfo elementInfo = view.getElementInfo(element);
    List<Node> childrenNodes = (elementInfo.children.size() == 0)
        ? Collections.<Node>emptyList()
        : new ArrayList<Node>(elementInfo.children.size());

    for (int i = 0, N = elementInfo.children.size(); i < N; ++i) {
      final Object childElement = elementInfo.children.get(i);
      Node childNode = createNodeForElement(childElement, view, processedElements);
      childrenNodes.add(childNode);
    }

    node.children = childrenNodes;
    node.childNodeCount = childrenNodes.size();

    return node;
  }

  private ChildNodeInsertedEvent acquireChildNodeInsertedEvent() {
    ChildNodeInsertedEvent childNodeInsertedEvent = mCachedChildNodeInsertedEvent;
    if (childNodeInsertedEvent == null) {
      childNodeInsertedEvent = new ChildNodeInsertedEvent();
    }
    mCachedChildNodeInsertedEvent = null;
    return childNodeInsertedEvent;
  }

  private void releaseChildNodeInsertedEvent(ChildNodeInsertedEvent childNodeInsertedEvent) {
    childNodeInsertedEvent.parentNodeId = -1;
    childNodeInsertedEvent.previousNodeId = -1;
    childNodeInsertedEvent.node = null;
    if (mCachedChildNodeInsertedEvent == null) {
      mCachedChildNodeInsertedEvent = childNodeInsertedEvent;
    }
  }

  private ChildNodeRemovedEvent acquireChildNodeRemovedEvent() {
    ChildNodeRemovedEvent childNodeRemovedEvent = mCachedChildNodeRemovedEvent;
    if (childNodeRemovedEvent == null) {
      childNodeRemovedEvent = new ChildNodeRemovedEvent();
    }
    mCachedChildNodeRemovedEvent = null;
    return childNodeRemovedEvent;
  }

  private void releaseChildNodeRemovedEvent(ChildNodeRemovedEvent childNodeRemovedEvent) {
    childNodeRemovedEvent.parentNodeId = -1;
    childNodeRemovedEvent.nodeId = -1;
    if (mCachedChildNodeRemovedEvent == null) {
      mCachedChildNodeRemovedEvent = childNodeRemovedEvent;
    }
  }

  private final class DocumentUpdateListener implements Document.UpdateListener {
    public void onAttributeModified(Object element, String name, String value) {
      AttributeModifiedEvent message = new AttributeModifiedEvent();
      message.nodeId = mDocument.getNodeIdForElement(element);
      message.name = name;
      message.value = value;
      mPeerManager.sendNotificationToPeers("DOM.attributeModified", message);
    }

    public void onAttributeRemoved(Object element, String name) {
      AttributeRemovedEvent message = new AttributeRemovedEvent();
      message.nodeId = mDocument.getNodeIdForElement(element);
      message.name = name;
      mPeerManager.sendNotificationToPeers("DOM.attributeRemoved", message);
    }

    public void onInspectRequested(Object element) {
      Integer nodeId = mDocument.getNodeIdForElement(element);
      if (nodeId == null) {
        LogUtil.d(
            "DocumentProvider.Listener.onInspectRequested() " +
                "called for a non-mapped node: element=%s",
            element);
      } else {
        InspectNodeRequestedEvent message = new InspectNodeRequestedEvent();
        message.nodeId = nodeId;
        mPeerManager.sendNotificationToPeers("DOM.inspectNodeRequested", message);
      }
    }

    public void onChildNodeRemoved(
        int parentNodeId,
        int nodeId) {
      ChildNodeRemovedEvent removedEvent = acquireChildNodeRemovedEvent();

      removedEvent.parentNodeId = parentNodeId;
      removedEvent.nodeId = nodeId;
      mPeerManager.sendNotificationToPeers("DOM.childNodeRemoved", removedEvent);

      releaseChildNodeRemovedEvent(removedEvent);
    }

    public void onChildNodeInserted(
        DocumentView view,
        Object element,
        int parentNodeId,
        int previousNodeId,
        Accumulator<Object> insertedElements) {
      ChildNodeInsertedEvent insertedEvent = acquireChildNodeInsertedEvent();

      insertedEvent.parentNodeId = parentNodeId;
      insertedEvent.previousNodeId = previousNodeId;
      insertedEvent.node = createNodeForElement(element, view, insertedElements);

      mPeerManager.sendNotificationToPeers("DOM.childNodeInserted", insertedEvent);

      releaseChildNodeInsertedEvent(insertedEvent);
    }
  }

  private final class PeerManagerListener extends PeersRegisteredListener {
    @Override
    protected synchronized void onFirstPeerRegistered() {
      mDocument.addRef();
      mDocument.addUpdateListener(mListener);
    }

    @Override
    protected synchronized void onLastPeerUnregistered() {
      mSearchResults.clear();
      mDocument.removeUpdateListener(mListener);
      mDocument.release();
    }
  }

  private static class GetDocumentResponse implements JsonRpcResult {
    @JsonProperty(required = true)
    public Node root;
  }

  private static class SetInspectedNodeRequest {
    @JsonProperty
    public int nodeId;
  }

  private static class GetNodeForLocationRequest implements JsonRpcResult {
    @JsonProperty(required = true)
    public int x;

    @JsonProperty(required = true)
    public int y;
  }

  private static class GetNodeForLocationResponse implements JsonRpcResult {
    @JsonProperty(required = true)
    public int nodeId;

    @JsonProperty
    public int backendNodeId;
  }

  private static class GetBoxModelRequest {
    @JsonProperty
    public int nodeId;
  }

  private static class GetBoxModelResponse implements JsonRpcResult {
    @JsonProperty(required = true)
    public BoxModel model;
  }

  private static class BoxModel implements JsonRpcResult {
    @JsonProperty(required = true)
    public int width;

    @JsonProperty(required = true)
    public int height;

    @JsonProperty(required = true)
    public List<Double> content;

    @JsonProperty(required = true)
    public List<Double> padding;

    @JsonProperty(required = true)
    public List<Double> border;

    @JsonProperty(required = true)
    public List<Double> margin;
  }

  private static class Node implements JsonRpcResult {
    @JsonProperty(required = true)
    public int nodeId;

    @JsonProperty(required = true)
    public NodeType nodeType;

    @JsonProperty(required = true)
    public String nodeName;

    @JsonProperty(required = true)
    public String localName;

    @JsonProperty(required = true)
    public String nodeValue;

    @JsonProperty
    public Integer childNodeCount;

    @JsonProperty
    public List<Node> children;

    @JsonProperty
    public List<String> attributes;

    // equals to nodeId
    @JsonProperty
    public int backendNodeId;
  }

  private static class PushNodesByBackendIdsToFrontendRequest {
    @JsonProperty
    public List<Integer> backendNodeIds;
  }

  private static class PushNodesByBackendIdsToFrontendResponse implements JsonRpcResult {
    @JsonProperty
    public List<Integer> nodeIds;
  }

  private static class AttributeModifiedEvent {
    @JsonProperty(required = true)
    public int nodeId;

    @JsonProperty(required = true)
    public String name;

    @JsonProperty(required = true)
    public String value;
  }

  private static class AttributeRemovedEvent {
    @JsonProperty(required = true)
    public int nodeId;

    @JsonProperty(required = true)
    public String name;
  }

  private static class ChildNodeInsertedEvent {
    @JsonProperty(required = true)
    public int parentNodeId;

    @JsonProperty(required = true)
    public int previousNodeId;

    @JsonProperty(required = true)
    public Node node;
  }

  private static class ChildNodeRemovedEvent {
    @JsonProperty(required = true)
    public int parentNodeId;

    @JsonProperty(required = true)
    public int nodeId;
  }

  private static class HighlightNodeRequest {
    @JsonProperty(required = true)
    public HighlightConfig highlightConfig;

    @JsonProperty
    public Integer nodeId;

    @JsonProperty
    public String objectId;
  }

  private static class HighlightConfig {
    @JsonProperty
    public RGBAColor contentColor;
  }

  private static class InspectNodeRequestedEvent {
    @JsonProperty
    public int nodeId;
  }

  private static class SetInspectModeEnabledRequest {
    @JsonProperty(required = true)
    public boolean enabled;

    @JsonProperty
    public Boolean inspectShadowDOM;

    @JsonProperty
    public HighlightConfig highlightConfig;
  }

  private static class RGBAColor {
    @JsonProperty(required = true)
    public int r;

    @JsonProperty(required = true)
    public int g;

    @JsonProperty(required = true)
    public int b;

    @JsonProperty
    public Double a;

    public int getColor() {
      byte alpha;
      if (this.a == null) {
        alpha = (byte)255;
      } else {
        long aLong = Math.round(this.a * 255.0);
        alpha = (aLong < 0) ? (byte)0 : (aLong >= 255) ? (byte)255 : (byte)aLong;
      }

      return Color.argb(alpha, this.r, this.g, this.b);
    }
  }

  private static class ResolveNodeRequest {
    @JsonProperty(required = true)
    public int nodeId;

    @JsonProperty
    public String objectGroup;
  }

  private static class SetAttributesAsTextRequest {
    @JsonProperty(required = true)
    public int nodeId;

    @JsonProperty(required = true)
    public String text;
  }

  private static class ResolveNodeResponse implements JsonRpcResult {
    @JsonProperty(required = true)
    public Runtime.RemoteObject object;
  }

  private static class PerformSearchRequest {
    @JsonProperty(required = true)
    public String query;

    @JsonProperty
    public Boolean includeUserAgentShadowDOM;
  }

  private static class PerformSearchResponse implements JsonRpcResult {
    @JsonProperty(required = true)
    public String searchId;

    @JsonProperty(required = true)
    public int resultCount;
  }

  private static class GetSearchResultsRequest {
    @JsonProperty(required = true)
    public String searchId;

    @JsonProperty(required = true)
    public int fromIndex;

    @JsonProperty(required = true)
    public int toIndex;
  }

  private static class GetSearchResultsResponse implements JsonRpcResult {
    @JsonProperty(required = true)
    public List<Integer> nodeIds;
  }

  private static class DiscardSearchResultsRequest {
    @JsonProperty(required = true)
    public String searchId;
  }
}
