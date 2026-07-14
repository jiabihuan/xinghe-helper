package com.xinghe.helper.cast;

import android.util.Log;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import fi.iki.elonen.NanoHTTPD;

import java.util.UUID;

public class DLNAHttpServer extends NanoHTTPD {

    private static final String TAG = "DLNAHttpServer";
    private final SSDPServer ssdp;
    private final CastState state;

    public DLNAHttpServer(int port, SSDPServer ssdp, CastState state) {
        super(port);
        this.ssdp = ssdp;
        this.state = state;
    }

    @Override
    public Response serve(IHTTPSession session) {
        String uri = session.getUri();
        Method method = session.getMethod();
        Log.d(TAG, method + " " + uri);

        try {
            if (method == Method.GET) {
                if (uri.equals("/description.xml")) {
                    return newFixedLengthResponse(Response.Status.OK, "text/xml; charset=utf-8", getDeviceDescription());
                }
                if (uri.startsWith("/avt/scpd.xml")) {
                    return newFixedLengthResponse(Response.Status.OK, "text/xml; charset=utf-8", getAVTransportSCPD());
                }
                if (uri.startsWith("/cms/scpd.xml")) {
                    return newFixedLengthResponse(Response.Status.OK, "text/xml; charset=utf-8", getConnectionManagerSCPD());
                }
                if (uri.startsWith("/rcs/scpd.xml")) {
                    return newFixedLengthResponse(Response.Status.OK, "text/xml; charset=utf-8", getRenderingControlSCPD());
                }
            }
            if (method == Method.POST) {
                Map<String, String> headers = session.getHeaders();
                String soapAction = headers.get("soapaction");
                if (soapAction == null) soapAction = headers.get("SOAPACTION");
                if (soapAction != null) soapAction = soapAction.replace("\"", "");

                Map<String, String> body = new HashMap<>();
                session.parseBody(body);
                String xml = body.get("postData");
                if (xml == null) xml = "";

                if (uri.startsWith("/avt/control")) {
                    return handleAVTransport(soapAction, xml);
                }
                if (uri.startsWith("/cms/control")) {
                    return handleConnectionManager(soapAction, xml);
                }
                if (uri.startsWith("/rcs/control")) {
                    return handleRenderingControl(soapAction, xml);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "serve error", e);
        }
        return newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found");
    }

    private Response handleAVTransport(String soapAction, String xml) {
        try {
            if (soapAction == null) {
                return soapError(401, "Invalid Action");
            }
            Log.d(TAG, "AVTransport action: " + soapAction);

            if (soapAction.contains("SetAVTransportURI")) {
                String url = extractArg(xml, "CurrentURI");
                String meta = extractArg(xml, "CurrentURIMetaData");
                state.setAVTransportURI(url, meta);
                return soapResponse("AVTransport", "SetAVTransportURI", "");
            }
            if (soapAction.contains("Play")) {
                state.play();
                return soapResponse("AVTransport", "Play", "");
            }
            if (soapAction.contains("Pause")) {
                state.pause();
                return soapResponse("AVTransport", "Pause", "");
            }
            if (soapAction.contains("Stop")) {
                state.stop();
                return soapResponse("AVTransport", "Stop", "");
            }
            if (soapAction.contains("Seek")) {
                String unit = extractArg(xml, "Unit");
                String target = extractArg(xml, "Target");
                long pos = parseTime(target);
                state.seek(pos);
                return soapResponse("AVTransport", "Seek", "");
            }
            if (soapAction.contains("GetTransportInfo")) {
                String body = "<CurrentTransportState>" + state.getTransportState() + "</CurrentTransportState>"
                        + "<CurrentTransportStatus>OK</CurrentTransportStatus>"
                        + "<CurrentSpeed>1</CurrentSpeed>";
                return soapResponse("AVTransport", "GetTransportInfo", body);
            }
            if (soapAction.contains("GetPositionInfo")) {
                String body = "<Track>1</Track>"
                        + "<TrackDuration>" + formatTime(state.getDuration()) + "</TrackDuration>"
                        + "<TrackMetaData></TrackMetaData>"
                        + "<TrackURI>" + state.getCurrentUrl() + "</TrackURI>"
                        + "<RelTime>" + formatTime(state.getPosition()) + "</RelTime>"
                        + "<AbsTime>" + formatTime(state.getPosition()) + "</AbsTime>"
                        + "<RelCount>0</RelCount>"
                        + "<AbsCount>0</AbsCount>";
                return soapResponse("AVTransport", "GetPositionInfo", body);
            }
            if (soapAction.contains("GetMediaInfo")) {
                String body = "<NrTracks>1</NrTracks>"
                        + "<MediaDuration>" + formatTime(state.getDuration()) + "</MediaDuration>"
                        + "<CurrentURI>" + state.getCurrentUrl() + "</CurrentURI>"
                        + "<CurrentURIMetaData></CurrentURIMetaData>"
                        + "<NextURI></NextURI>"
                        + "<NextURIMetaData></NextURIMetaData>"
                        + "<PlayMedium>NETWORK</PlayMedium>"
                        + "<RecordMedium>NOT_IMPLEMENTED</RecordMedium>"
                        + "<WriteStatus>NOT_IMPLEMENTED</WriteStatus>";
                return soapResponse("AVTransport", "GetMediaInfo", body);
            }
            if (soapAction.contains("GetDeviceCapabilities")) {
                String body = "<PlayMedia>NONE</PlayMedia>"
                        + "<RecMedia>NOT_IMPLEMENTED</RecMedia>"
                        + "<RecQualityModes>NOT_IMPLEMENTED</RecQualityModes>";
                return soapResponse("AVTransport", "GetDeviceCapabilities", body);
            }
            if (soapAction.contains("GetTransportSettings")) {
                String body = "<PlayMode>NORMAL</PlayMode><RecQualityMode>NOT_IMPLEMENTED</RecQualityMode>";
                return soapResponse("AVTransport", "GetTransportSettings", body);
            }
            if (soapAction.contains("SetPlayMode")) {
                return soapResponse("AVTransport", "SetPlayMode", "");
            }
            if (soapAction.contains("Next")) {
                return soapResponse("AVTransport", "Next", "");
            }
            if (soapAction.contains("Previous")) {
                return soapResponse("AVTransport", "Previous", "");
            }
            if (soapAction.contains("SetNextAVTransportURI")) {
                return soapResponse("AVTransport", "SetNextAVTransportURI", "");
            }
        } catch (Exception e) {
            Log.e(TAG, "handleAVTransport error", e);
        }
        return soapError(401, "Invalid Action");
    }

    private Response handleConnectionManager(String soapAction, String xml) {
        try {
            if (soapAction == null) return soapError(401, "Invalid Action");
            Log.d(TAG, "ConnectionManager action: " + soapAction);

            if (soapAction.contains("GetProtocolInfo")) {
                String body = "<Source>" +
                        "http-get:*:video/mp4:DLNA.ORG_PN=AVC_MP4_BL_CIF15_AAC_520;" +
                        "http-get:*:video/mpeg:DLNA.ORG_PN=MPEG_PS_PAL;" +
                        "http-get:*:video/x-matroska:*;" +
                        "http-get:*:video/avi:*;" +
                        "http-get:*:audio/mpeg:*;" +
                        "http-get:*:audio/mp4:*;" +
                        "http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_SM;" +
                        "http-get:*:image/png:*" +
                        "</Source><Sink>" +
                        "http-get:*:video/mp4:DLNA.ORG_PN=AVC_MP4_BL_CIF15_AAC_520;" +
                        "http-get:*:video/mpeg:DLNA.ORG_PN=MPEG_PS_PAL;" +
                        "http-get:*:video/x-matroska:*;" +
                        "http-get:*:video/avi:*;" +
                        "http-get:*:audio/mpeg:*;" +
                        "http-get:*:audio/mp4:*;" +
                        "http-get:*:image/jpeg:DLNA.ORG_PN=JPEG_SM;" +
                        "http-get:*:image/png:*" +
                        "</Sink>";
                return soapResponse("ConnectionManager", "GetProtocolInfo", body);
            }
            if (soapAction.contains("PrepareForConnection")) {
                String body = "<ConnectionID>0</ConnectionID>" +
                        "<AVTransportID>0</AVTransportID>" +
                        "<RcsID>0</RcsID>";
                return soapResponse("ConnectionManager", "PrepareForConnection", body);
            }
            if (soapAction.contains("ConnectionComplete")) {
                return soapResponse("ConnectionManager", "ConnectionComplete", "");
            }
            if (soapAction.contains("GetCurrentConnectionIDs")) {
                return soapResponse("ConnectionManager", "GetCurrentConnectionIDs",
                        "<ConnectionIDs>0</ConnectionIDs>");
            }
            if (soapAction.contains("GetCurrentConnectionInfo")) {
                String body = "<RcsID>0</RcsID>" +
                        "<AVTransportID>0</AVTransportID>" +
                        "<ProtocolInfo></ProtocolInfo>" +
                        "<PeerConnectionManager></PeerConnectionManager>" +
                        "<PeerConnectionID>0</PeerConnectionID>" +
                        "<Direction>Input</Direction>" +
                        "<Status>OK</Status>";
                return soapResponse("ConnectionManager", "GetCurrentConnectionInfo", body);
            }
        } catch (Exception e) {
            Log.e(TAG, "handleConnectionManager error", e);
        }
        return soapError(401, "Invalid Action");
    }

    private Response handleRenderingControl(String soapAction, String xml) {
        try {
            if (soapAction == null) return soapError(401, "Invalid Action");
            Log.d(TAG, "RenderingControl action: " + soapAction);

            if (soapAction.contains("SetVolume")) {
                String volStr = extractArg(xml, "DesiredVolume");
                try {
                    int vol = Integer.parseInt(volStr);
                    state.setVolume(vol);
                } catch (NumberFormatException ignored) {}
                return soapResponse("RenderingControl", "SetVolume", "");
            }
            if (soapAction.contains("GetVolume")) {
                return soapResponse("RenderingControl", "GetVolume",
                        "<CurrentVolume>" + state.getVolume() + "</CurrentVolume>");
            }
            if (soapAction.contains("SetMute")) {
                String muteStr = extractArg(xml, "DesiredMute");
                state.setMute(!"0".equals(muteStr));
                return soapResponse("RenderingControl", "SetMute", "");
            }
            if (soapAction.contains("GetMute")) {
                return soapResponse("RenderingControl", "GetMute",
                        "<CurrentMute>" + (state.isMute() ? "1" : "0") + "</CurrentMute>");
            }
            if (soapAction.contains("ListPresets")) {
                return soapResponse("RenderingControl", "ListPresets",
                        "<CurrentPresetNameList>FactoryDefaults</CurrentPresetNameList>");
            }
            if (soapAction.contains("SelectPreset")) {
                return soapResponse("RenderingControl", "SelectPreset", "");
            }
        } catch (Exception e) {
            Log.e(TAG, "handleRenderingControl error", e);
        }
        return soapError(401, "Invalid Action");
    }

    private Response soapResponse(String service, String action, String body) {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n" +
                "<s:Body>\r\n" +
                "<u:" + action + "Response xmlns:u=\"urn:schemas-upnp-org:service:" + service + ":1\">\r\n" +
                body + "\r\n" +
                "</u:" + action + "Response>\r\n" +
                "</s:Body>\r\n" +
                "</s:Envelope>\r\n";
        Response resp = newFixedLengthResponse(Response.Status.OK, "text/xml; charset=utf-8", xml);
        resp.addHeader("EXT", "");
        return resp;
    }

    private Response soapError(int code, String desc) {
        String xml = "<?xml version=\"1.0\"?>\r\n" +
                "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
                "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">\r\n" +
                "<s:Body>\r\n" +
                "<s:Fault>\r\n" +
                "<faultcode>s:Client</faultcode>\r\n" +
                "<faultstring>UPnPError</faultstring>\r\n" +
                "<detail>\r\n" +
                "<UPnPError xmlns=\"urn:schemas-upnp-org:control-1-0\">\r\n" +
                "<errorCode>" + code + "</errorCode>\r\n" +
                "<errorDescription>" + desc + "</errorDescription>\r\n" +
                "</UPnPError>\r\n" +
                "</detail>\r\n" +
                "</s:Fault>\r\n" +
                "</s:Body>\r\n" +
                "</s:Envelope>\r\n";
        return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, "text/xml; charset=utf-8", xml);
    }

    private String extractArg(String xml, String name) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes("UTF-8")));
            NodeList nodes = doc.getElementsByTagName(name);
            if (nodes.getLength() > 0) {
                return nodes.item(0).getTextContent();
            }
        } catch (Exception e) {
            Log.e(TAG, "extractArg " + name + " error", e);
        }
        return "";
    }

    private long parseTime(String time) {
        if (time == null || time.isEmpty()) return 0;
        try {
            String[] parts = time.split(":");
            if (parts.length == 3) {
                long h = Long.parseLong(parts[0]);
                long m = Long.parseLong(parts[1]);
                double s = Double.parseDouble(parts[2]);
                return (long) ((h * 3600 + m * 60 + s) * 1000);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private String formatTime(long ms) {
        if (ms <= 0) return "00:00:00.000";
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d.000", h, m, s);
    }

    private String getDeviceDescription() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
                "<root xmlns=\"urn:schemas-upnp-org:device-1-0\">\r\n" +
                "<specVersion><major>1</major><minor>0</minor></specVersion>\r\n" +
                "<device>\r\n" +
                "<deviceType>urn:schemas-upnp-org:device:MediaRenderer:1</deviceType>\r\n" +
                "<friendlyName>" + ssdp.getDeviceName() + "</friendlyName>\r\n" +
                "<manufacturer>XingHe</manufacturer>\r\n" +
                "<manufacturerURL>http://www.xinghe.com</manufacturerURL>\r\n" +
                "<modelDescription>DLNA MediaRenderer</modelDescription>\r\n" +
                "<modelName>XingHe Cast</modelName>\r\n" +
                "<modelNumber>1.0</modelNumber>\r\n" +
                "<modelURL>http://www.xinghe.com</modelURL>\r\n" +
                "<serialNumber>00000001</serialNumber>\r\n" +
                "<UDN>uuid:" + ssdp.getUuid() + "</UDN>\r\n" +
                "<iconList>\r\n" +
                "<icon><mimetype>image/png</mimetype><width>48</width><height>48</height><depth>24</depth><url>/icon.png</url></icon>\r\n" +
                "</iconList>\r\n" +
                "<serviceList>\r\n" +
                "<service>\r\n" +
                "<serviceType>urn:schemas-upnp-org:service:AVTransport:1</serviceType>\r\n" +
                "<serviceId>urn:upnp-org:serviceId:AVTransport</serviceId>\r\n" +
                "<SCPDURL>/avt/scpd.xml</SCPDURL>\r\n" +
                "<controlURL>/avt/control</controlURL>\r\n" +
                "<eventSubURL>/avt/event</eventSubURL>\r\n" +
                "</service>\r\n" +
                "<service>\r\n" +
                "<serviceType>urn:schemas-upnp-org:service:ConnectionManager:1</serviceType>\r\n" +
                "<serviceId>urn:upnp-org:serviceId:ConnectionManager</serviceId>\r\n" +
                "<SCPDURL>/cms/scpd.xml</SCPDURL>\r\n" +
                "<controlURL>/cms/control</controlURL>\r\n" +
                "<eventSubURL>/cms/event</eventSubURL>\r\n" +
                "</service>\r\n" +
                "<service>\r\n" +
                "<serviceType>urn:schemas-upnp-org:service:RenderingControl:1</serviceType>\r\n" +
                "<serviceId>urn:upnp-org:serviceId:RenderingControl</serviceId>\r\n" +
                "<SCPDURL>/rcs/scpd.xml</SCPDURL>\r\n" +
                "<controlURL>/rcs/control</controlURL>\r\n" +
                "<eventSubURL>/rcs/event</eventSubURL>\r\n" +
                "</service>\r\n" +
                "</serviceList>\r\n" +
                "</device>\r\n" +
                "</root>\r\n";
    }

    private String getAVTransportSCPD() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
                "<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">\r\n" +
                "<specVersion><major>1</major><minor>0</minor></specVersion>\r\n" +
                "<actionList>\r\n" +
                "<action><name>SetAVTransportURI</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "<argument><name>CurrentURI</name><direction>in</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>\r\n" +
                "<argument><name>CurrentURIMetaData</name><direction>in</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>GetMediaInfo</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "<argument><name>NrTracks</name><direction>out</direction><relatedStateVariable>NumberOfTracks</relatedStateVariable></argument>\r\n" +
                "<argument><name>MediaDuration</name><direction>out</direction><relatedStateVariable>CurrentMediaDuration</relatedStateVariable></argument>\r\n" +
                "<argument><name>CurrentURI</name><direction>out</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>\r\n" +
                "<argument><name>CurrentURIMetaData</name><direction>out</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>\r\n" +
                "<argument><name>NextURI</name><direction>out</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>\r\n" +
                "<argument><name>NextURIMetaData</name><direction>out</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>\r\n" +
                "<argument><name>PlayMedium</name><direction>out</direction><relatedStateVariable>PlaybackStorageMedium</relatedStateVariable></argument>\r\n" +
                "<argument><name>RecordMedium</name><direction>out</direction><relatedStateVariable>RecordStorageMedium</relatedStateVariable></argument>\r\n" +
                "<argument><name>WriteStatus</name><direction>out</direction><relatedStateVariable>RecordMediumWriteStatus</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>GetTransportInfo</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "<argument><name>CurrentTransportState</name><direction>out</direction><relatedStateVariable>TransportState</relatedStateVariable></argument>\r\n" +
                "<argument><name>CurrentTransportStatus</name><direction>out</direction><relatedStateVariable>TransportStatus</relatedStateVariable></argument>\r\n" +
                "<argument><name>CurrentSpeed</name><direction>out</direction><relatedStateVariable>TransportPlaySpeed</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>GetPositionInfo</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "<argument><name>Track</name><direction>out</direction><relatedStateVariable>Track</relatedStateVariable></argument>\r\n" +
                "<argument><name>TrackDuration</name><direction>out</direction><relatedStateVariable>CurrentTrackDuration</relatedStateVariable></argument>\r\n" +
                "<argument><name>TrackMetaData</name><direction>out</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>\r\n" +
                "<argument><name>TrackURI</name><direction>out</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>\r\n" +
                "<argument><name>RelTime</name><direction>out</direction><relatedStateVariable>RelativeTimePosition</relatedStateVariable></argument>\r\n" +
                "<argument><name>AbsTime</name><direction>out</direction><relatedStateVariable>AbsoluteTimePosition</relatedStateVariable></argument>\r\n" +
                "<argument><name>RelCount</name><direction>out</direction><relatedStateVariable>RelativeCounterPosition</relatedStateVariable></argument>\r\n" +
                "<argument><name>AbsCount</name><direction>out</direction><relatedStateVariable>AbsoluteCounterPosition</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>GetDeviceCapabilities</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "<argument><name>PlayMedia</name><direction>out</direction><relatedStateVariable>PossiblePlaybackStorageMedia</relatedStateVariable></argument>\r\n" +
                "<argument><name>RecMedia</name><direction>out</direction><relatedStateVariable>PossibleRecordStorageMedia</relatedStateVariable></argument>\r\n" +
                "<argument><name>RecQualityModes</name><direction>out</direction><relatedStateVariable>PossibleRecordQualityModes</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>GetTransportSettings</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "<argument><name>PlayMode</name><direction>out</direction><relatedStateVariable>CurrentPlayMode</relatedStateVariable></argument>\r\n" +
                "<argument><name>RecQualityMode</name><direction>out</direction><relatedStateVariable>CurrentRecordQualityMode</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>Stop</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>Play</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "<argument><name>Speed</name><direction>in</direction><relatedStateVariable>TransportPlaySpeed</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>Pause</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>Seek</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "<argument><name>Unit</name><direction>in</direction><relatedStateVariable>SeekMode</relatedStateVariable></argument>\r\n" +
                "<argument><name>Target</name><direction>in</direction><relatedStateVariable>SeekTarget</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>Next</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>Previous</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>SetPlayMode</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "<argument><name>NewPlayMode</name><direction>in</direction><relatedStateVariable>CurrentPlayMode</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>SetNextAVTransportURI</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "<argument><name>NextURI</name><direction>in</direction><relatedStateVariable>AVTransportURI</relatedStateVariable></argument>\r\n" +
                "<argument><name>NextURIMetaData</name><direction>in</direction><relatedStateVariable>AVTransportURIMetaData</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "</actionList>\r\n" +
                "<serviceStateTable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_InstanceID</name><dataType>ui4</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>AVTransportURI</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>AVTransportURIMetaData</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>CurrentMediaDuration</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>NumberOfTracks</name><dataType>ui4</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>CurrentTrackDuration</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>Track</name><dataType>ui4</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>RelativeTimePosition</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>AbsoluteTimePosition</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>RelativeCounterPosition</name><dataType>i4</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>AbsoluteCounterPosition</name><dataType>i4</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"yes\"><name>TransportState</name><dataType>string</dataType>" +
                "<allowedValueList><allowedValue>STOPPED</allowedValue><allowedValue>PLAYING</allowedValue>" +
                "<allowedValue>TRANSITIONING</allowedValue><allowedValue>PAUSED_PLAYBACK</allowedValue>" +
                "<allowedValue>PAUSED_RECORDING</allowedValue><allowedValue>RECORDING</allowedValue>" +
                "<allowedValue>NO_MEDIA_PRESENT</allowedValue></allowedValueList></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>TransportStatus</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>TransportPlaySpeed</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>CurrentPlayMode</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>CurrentRecordQualityMode</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>PlaybackStorageMedium</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>RecordStorageMedium</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>RecordMediumWriteStatus</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>PossiblePlaybackStorageMedia</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>PossibleRecordStorageMedia</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>PossibleRecordQualityModes</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>SeekMode</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>SeekTarget</name><dataType>string</dataType></stateVariable>\r\n" +
                "</serviceStateTable>\r\n" +
                "</scpd>";
    }

    private String getConnectionManagerSCPD() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
                "<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">\r\n" +
                "<specVersion><major>1</major><minor>0</minor></specVersion>\r\n" +
                "<actionList>\r\n" +
                "<action><name>GetProtocolInfo</name><argumentList>\r\n" +
                "<argument><name>Source</name><direction>out</direction><relatedStateVariable>SourceProtocolInfo</relatedStateVariable></argument>\r\n" +
                "<argument><name>Sink</name><direction>out</direction><relatedStateVariable>SinkProtocolInfo</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>PrepareForConnection</name><argumentList>\r\n" +
                "<argument><name>RemoteProtocolInfo</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_ProtocolInfo</relatedStateVariable></argument>\r\n" +
                "<argument><name>PeerConnectionManager</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_ConnectionManager</relatedStateVariable></argument>\r\n" +
                "<argument><name>PeerConnectionID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_ConnectionID</relatedStateVariable></argument>\r\n" +
                "<argument><name>Direction</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Direction</relatedStateVariable></argument>\r\n" +
                "<argument><name>ConnectionID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ConnectionID</relatedStateVariable></argument>\r\n" +
                "<argument><name>AVTransportID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_AVTransportID</relatedStateVariable></argument>\r\n" +
                "<argument><name>RcsID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_RcsID</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>ConnectionComplete</name><argumentList>\r\n" +
                "<argument><name>ConnectionID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_ConnectionID</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>GetCurrentConnectionIDs</name><argumentList>\r\n" +
                "<argument><name>ConnectionIDs</name><direction>out</direction><relatedStateVariable>CurrentConnectionIDs</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>GetCurrentConnectionInfo</name><argumentList>\r\n" +
                "<argument><name>ConnectionID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_ConnectionID</relatedStateVariable></argument>\r\n" +
                "<argument><name>RcsID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_RcsID</relatedStateVariable></argument>\r\n" +
                "<argument><name>AVTransportID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_AVTransportID</relatedStateVariable></argument>\r\n" +
                "<argument><name>ProtocolInfo</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ProtocolInfo</relatedStateVariable></argument>\r\n" +
                "<argument><name>PeerConnectionManager</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ConnectionManager</relatedStateVariable></argument>\r\n" +
                "<argument><name>PeerConnectionID</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ConnectionID</relatedStateVariable></argument>\r\n" +
                "<argument><name>Direction</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_Direction</relatedStateVariable></argument>\r\n" +
                "<argument><name>Status</name><direction>out</direction><relatedStateVariable>A_ARG_TYPE_ConnectionStatus</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "</actionList>\r\n" +
                "<serviceStateTable>\r\n" +
                "<stateVariable sendEvents=\"yes\"><name>SourceProtocolInfo</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"yes\"><name>SinkProtocolInfo</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"yes\"><name>CurrentConnectionIDs</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_ConnectionID</name><dataType>i4</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_AVTransportID</name><dataType>i4</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_RcsID</name><dataType>i4</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_ProtocolInfo</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_ConnectionManager</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_Direction</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_ConnectionStatus</name><dataType>string</dataType></stateVariable>\r\n" +
                "</serviceStateTable>\r\n" +
                "</scpd>";
    }

    private String getRenderingControlSCPD() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
                "<scpd xmlns=\"urn:schemas-upnp-org:service-1-0\">\r\n" +
                "<specVersion><major>1</major><minor>0</minor></specVersion>\r\n" +
                "<actionList>\r\n" +
                "<action><name>ListPresets</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "<argument><name>CurrentPresetNameList</name><direction>out</direction><relatedStateVariable>PresetNameList</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>SelectPreset</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "<argument><name>PresetName</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_PresetName</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>GetVolume</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "<argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>\r\n" +
                "<argument><name>CurrentVolume</name><direction>out</direction><relatedStateVariable>Volume</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>SetVolume</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "<argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>\r\n" +
                "<argument><name>DesiredVolume</name><direction>in</direction><relatedStateVariable>Volume</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>GetMute</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "<argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>\r\n" +
                "<argument><name>CurrentMute</name><direction>out</direction><relatedStateVariable>Mute</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "<action><name>SetMute</name><argumentList>\r\n" +
                "<argument><name>InstanceID</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_InstanceID</relatedStateVariable></argument>\r\n" +
                "<argument><name>Channel</name><direction>in</direction><relatedStateVariable>A_ARG_TYPE_Channel</relatedStateVariable></argument>\r\n" +
                "<argument><name>DesiredMute</name><direction>in</direction><relatedStateVariable>Mute</relatedStateVariable></argument>\r\n" +
                "</argumentList></action>\r\n" +
                "</actionList>\r\n" +
                "<serviceStateTable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_InstanceID</name><dataType>ui4</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_Channel</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>A_ARG_TYPE_PresetName</name><dataType>string</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"yes\"><name>Volume</name><dataType>ui2</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"yes\"><name>Mute</name><dataType>boolean</dataType></stateVariable>\r\n" +
                "<stateVariable sendEvents=\"no\"><name>PresetNameList</name><dataType>string</dataType></stateVariable>\r\n" +
                "</serviceStateTable>\r\n" +
                "</scpd>";
    }
}
