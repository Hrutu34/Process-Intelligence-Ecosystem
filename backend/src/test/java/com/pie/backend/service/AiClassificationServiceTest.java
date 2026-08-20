package com.pie.backend.service;
import com.pie.shared.dto.ClassificationResultDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class AiClassificationServiceTest {

    @Test
    void classify_withMapResponse_returnsClassification() throws Exception {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(chatClient);

        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(eq(Map.class)))
                .thenReturn(Map.of("category", "Meeting Notes", "confidence", 85));

        AiClassificationService svc = new AiClassificationService(builder);
        ClassificationResultDTO res = svc
                .classifyDocument("# Car Manufacturing Process – Detailed Process Document\r\n" + //
                        "\r\n" + //
                        "## 1. Purpose\r\n" + //
                        "\r\n" + //
                        "The purpose of this document is to describe the major processes and important steps involved in manufacturing a car, from initial product planning and engineering through production, quality inspection, vehicle testing, and final delivery.\r\n"
                        + //
                        "\r\n" + //
                        "The process is designed to ensure that every vehicle is manufactured safely, consistently, efficiently, and according to the required engineering specifications and quality standards.\r\n"
                        + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "## 2. Overall Manufacturing Process\r\n" + //
                        "\r\n" + //
                        "The typical car manufacturing flow is:\r\n" + //
                        "\r\n" + //
                        "**Product Planning → Vehicle Design → Engineering → Supplier Development → Parts Receiving → Stamping → Body Shop → Paint Shop → Powertrain Assembly → Trim & Chassis → Final Assembly → Inspection → Testing → Final Quality Audit → Vehicle Dispatch**\r\n"
                        + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 3. Product Planning\r\n" + //
                        "\r\n" + //
                        "The manufacturing process starts with defining the vehicle requirements.\r\n" + //
                        "\r\n" + //
                        "### Important activities\r\n" + //
                        "\r\n" + //
                        "1. Identify the target customer.\r\n" + //
                        "2. Define vehicle type:\r\n" + //
                        "\r\n" + //
                        "   * Hatchback\r\n" + //
                        "   * Sedan\r\n" + //
                        "   * SUV\r\n" + //
                        "   * MPV\r\n" + //
                        "   * Pickup\r\n" + //
                        "   * Electric vehicle\r\n" + //
                        "3. Define vehicle specifications.\r\n" + //
                        "4. Define target cost.\r\n" + //
                        "5. Define production volume.\r\n" + //
                        "6. Define safety requirements.\r\n" + //
                        "7. Define emission requirements for applicable vehicles.\r\n" + //
                        "8. Define performance requirements.\r\n" + //
                        "9. Define expected vehicle life.\r\n" + //
                        "10. Establish project timing and milestones.\r\n" + //
                        "\r\n" + //
                        "### Key outputs\r\n" + //
                        "\r\n" + //
                        "* Vehicle specification\r\n" + //
                        "* Target cost\r\n" + //
                        "* Production volume\r\n" + //
                        "* Project timeline\r\n" + //
                        "* Product requirements\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 4. Vehicle Design and Engineering\r\n" + //
                        "\r\n" + //
                        "The engineering team converts the product requirements into a manufacturable vehicle design.\r\n"
                        + //
                        "\r\n" + //
                        "### Main engineering areas\r\n" + //
                        "\r\n" + //
                        "* Body structure\r\n" + //
                        "* Chassis\r\n" + //
                        "* Suspension\r\n" + //
                        "* Steering\r\n" + //
                        "* Braking system\r\n" + //
                        "* Engine or electric motor\r\n" + //
                        "* Transmission/reduction gearbox\r\n" + //
                        "* Electrical system\r\n" + //
                        "* Interior\r\n" + //
                        "* Exterior\r\n" + //
                        "* HVAC\r\n" + //
                        "* Safety systems\r\n" + //
                        "* Infotainment\r\n" + //
                        "* Software and electronic control systems\r\n" + //
                        "\r\n" + //
                        "### Important engineering activities\r\n" + //
                        "\r\n" + //
                        "1. Create the vehicle architecture.\r\n" + //
                        "2. Develop 3D CAD models.\r\n" + //
                        "3. Design individual components.\r\n" + //
                        "4. Perform structural analysis.\r\n" + //
                        "5. Perform crash simulations.\r\n" + //
                        "6. Perform thermal analysis.\r\n" + //
                        "7. Perform durability analysis.\r\n" + //
                        "8. Design manufacturing processes.\r\n" + //
                        "9. Create engineering drawings.\r\n" + //
                        "10. Define component specifications and tolerances.\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 5. Supplier Development and Procurement\r\n" + //
                        "\r\n" + //
                        "Most vehicle manufacturers purchase thousands of components from suppliers.\r\n" + //
                        "\r\n" + //
                        "Examples include:\r\n" + //
                        "\r\n" + //
                        "* Seats\r\n" + //
                        "* Tyres\r\n" + //
                        "* Glass\r\n" + //
                        "* Batteries\r\n" + //
                        "* Wiring harnesses\r\n" + //
                        "* Electronic control units\r\n" + //
                        "* Braking components\r\n" + //
                        "* Suspension components\r\n" + //
                        "* Plastic components\r\n" + //
                        "* Lighting systems\r\n" + //
                        "* Interior components\r\n" + //
                        "\r\n" + //
                        "### Important steps\r\n" + //
                        "\r\n" + //
                        "1. Identify potential suppliers.\r\n" + //
                        "2. Evaluate supplier capability.\r\n" + //
                        "3. Approve suppliers.\r\n" + //
                        "4. Release component specifications.\r\n" + //
                        "5. Develop prototype parts.\r\n" + //
                        "6. Conduct supplier quality audits.\r\n" + //
                        "7. Validate components.\r\n" + //
                        "8. Establish production capacity.\r\n" + //
                        "9. Approve the production process.\r\n" + //
                        "10. Establish logistics and delivery schedules.\r\n" + //
                        "\r\n" + //
                        "### Key objective\r\n" + //
                        "\r\n" + //
                        "Ensure that the correct quantity and quality of parts are available at the manufacturing plant when required.\r\n"
                        + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 6. Incoming Material and Parts Inspection\r\n" + //
                        "\r\n" + //
                        "When components arrive at the manufacturing plant, they are received and inspected.\r\n" + //
                        "\r\n" + //
                        "### Process\r\n" + //
                        "\r\n" + //
                        "**Truck Arrival → Documentation Check → Quantity Verification → Quality Inspection → Acceptance → Storage/Line Supply**\r\n"
                        + //
                        "\r\n" + //
                        "### Inspection may include\r\n" + //
                        "\r\n" + //
                        "* Dimensions\r\n" + //
                        "* Material verification\r\n" + //
                        "* Appearance\r\n" + //
                        "* Functional checks\r\n" + //
                        "* Electrical testing\r\n" + //
                        "* Packaging condition\r\n" + //
                        "* Part identification\r\n" + //
                        "* Traceability information\r\n" + //
                        "\r\n" + //
                        "Non-conforming parts are normally identified, segregated, and handled through the plant's non-conformance process.\r\n"
                        + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 7. Sheet Metal Stamping\r\n" + //
                        "\r\n" + //
                        "Large steel or aluminium sheets are converted into vehicle body panels using stamping presses.\r\n"
                        + //
                        "\r\n" + //
                        "Examples:\r\n" + //
                        "\r\n" + //
                        "* Hood\r\n" + //
                        "* Roof\r\n" + //
                        "* Doors\r\n" + //
                        "* Fenders\r\n" + //
                        "* Quarter panels\r\n" + //
                        "* Floor panels\r\n" + //
                        "\r\n" + //
                        "### Main process\r\n" + //
                        "\r\n" + //
                        "**Sheet Coil/Blank → Cutting → Pressing → Forming → Trimming → Inspection**\r\n" + //
                        "\r\n" + //
                        "### Important controls\r\n" + //
                        "\r\n" + //
                        "* Material specification\r\n" + //
                        "* Sheet thickness\r\n" + //
                        "* Press parameters\r\n" + //
                        "* Die condition\r\n" + //
                        "* Panel dimensions\r\n" + //
                        "* Surface defects\r\n" + //
                        "* Cracks\r\n" + //
                        "* Wrinkles\r\n" + //
                        "* Dent marks\r\n" + //
                        "\r\n" + //
                        "The finished panels are transferred to the body shop.\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 8. Body Shop\r\n" + //
                        "\r\n" + //
                        "The body shop joins stamped panels and structural components to create the vehicle body shell.\r\n"
                        + //
                        "\r\n" + //
                        "### Major activities\r\n" + //
                        "\r\n" + //
                        "1. Load body components.\r\n" + //
                        "2. Position components in fixtures.\r\n" + //
                        "3. Perform spot welding.\r\n" + //
                        "4. Perform laser or other approved joining processes where applicable.\r\n" + //
                        "5. Install structural reinforcements.\r\n" + //
                        "6. Join floor, side body, roof, and other major structures.\r\n" + //
                        "7. Check body dimensions.\r\n" + //
                        "8. Perform dimensional inspection.\r\n" + //
                        "\r\n" + //
                        "### Typical body sequence\r\n" + //
                        "\r\n" + //
                        "**Underbody → Side Body → Roof → Closures → Body-in-White**\r\n" + //
                        "\r\n" + //
                        "The completed unpainted vehicle body is commonly called **Body-in-White (BIW)**.\r\n" + //
                        "\r\n" + //
                        "### Important quality characteristics\r\n" + //
                        "\r\n" + //
                        "* Body dimensions\r\n" + //
                        "* Welding quality\r\n" + //
                        "* Panel gaps\r\n" + //
                        "* Panel alignment\r\n" + //
                        "* Weld count and location\r\n" + //
                        "* Structural integrity\r\n" + //
                        "* Surface condition\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 9. Pre-Treatment and Paint Shop\r\n" + //
                        "\r\n" + //
                        "The BIW is transferred to the paint shop.\r\n" + //
                        "\r\n" + //
                        "The paint process protects the vehicle body from corrosion and provides the required appearance.\r\n"
                        + //
                        "\r\n" + //
                        "### Typical process\r\n" + //
                        "\r\n" + //
                        "**Cleaning → Pre-Treatment → E-Coating → Sealing → Primer → Base Coat → Clear Coat → Oven/Curing → Inspection**\r\n"
                        + //
                        "\r\n" + //
                        "### 9.1 Cleaning\r\n" + //
                        "\r\n" + //
                        "Oil, dust, and other contaminants are removed from the body.\r\n" + //
                        "\r\n" + //
                        "### 9.2 Pre-Treatment\r\n" + //
                        "\r\n" + //
                        "The body receives chemical treatment to improve corrosion protection and coating adhesion.\r\n"
                        + //
                        "\r\n" + //
                        "### 9.3 E-Coat\r\n" + //
                        "\r\n" + //
                        "An electro-coating process provides corrosion protection, including areas that are difficult to reach with conventional painting.\r\n"
                        + //
                        "\r\n" + //
                        "### 9.4 Sealing\r\n" + //
                        "\r\n" + //
                        "Sealants are applied to prevent water, dust, and noise intrusion.\r\n" + //
                        "\r\n" + //
                        "### 9.5 Primer\r\n" + //
                        "\r\n" + //
                        "Primer provides additional protection and prepares the surface for the final paint layers.\r\n"
                        + //
                        "\r\n" + //
                        "### 9.6 Base Coat\r\n" + //
                        "\r\n" + //
                        "The vehicle receives its specified exterior color.\r\n" + //
                        "\r\n" + //
                        "### 9.7 Clear Coat\r\n" + //
                        "\r\n" + //
                        "Clear coat provides protection, gloss, and improved surface durability.\r\n" + //
                        "\r\n" + //
                        "### 9.8 Paint Inspection\r\n" + //
                        "\r\n" + //
                        "Inspect for:\r\n" + //
                        "\r\n" + //
                        "* Color variation\r\n" + //
                        "* Dust\r\n" + //
                        "* Runs\r\n" + //
                        "* Orange peel\r\n" + //
                        "* Scratches\r\n" + //
                        "* Surface contamination\r\n" + //
                        "* Paint thickness\r\n" + //
                        "* Gloss\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 10. Powertrain Manufacturing and Assembly\r\n" + //
                        "\r\n" + //
                        "Depending on the vehicle type, the powertrain may include an internal combustion engine, hybrid system, or electric drive unit.\r\n"
                        + //
                        "\r\n" + //
                        "## Internal Combustion Vehicle\r\n" + //
                        "\r\n" + //
                        "Typical engine assembly includes:\r\n" + //
                        "\r\n" + //
                        "1. Engine block preparation\r\n" + //
                        "2. Crankshaft installation\r\n" + //
                        "3. Piston and connecting rod installation\r\n" + //
                        "4. Cylinder head installation\r\n" + //
                        "5. Timing system installation\r\n" + //
                        "6. Lubrication system installation\r\n" + //
                        "7. Cooling system installation\r\n" + //
                        "8. Fuel system installation\r\n" + //
                        "9. Intake and exhaust components\r\n" + //
                        "10. Sensors and electrical connections\r\n" + //
                        "\r\n" + //
                        "## Transmission\r\n" + //
                        "\r\n" + //
                        "Typical activities include:\r\n" + //
                        "\r\n" + //
                        "* Gear installation\r\n" + //
                        "* Shaft installation\r\n" + //
                        "* Bearing installation\r\n" + //
                        "* Housing assembly\r\n" + //
                        "* Lubrication\r\n" + //
                        "* Adjustment\r\n" + //
                        "* Functional testing\r\n" + //
                        "\r\n" + //
                        "## Electric Vehicle\r\n" + //
                        "\r\n" + //
                        "Typical EV powertrain activities may include:\r\n" + //
                        "\r\n" + //
                        "* Battery pack assembly\r\n" + //
                        "* Battery management system installation\r\n" + //
                        "* Electric motor assembly\r\n" + //
                        "* Inverter installation\r\n" + //
                        "* High-voltage cable installation\r\n" + //
                        "* Cooling system installation\r\n" + //
                        "* Electrical safety checks\r\n" + //
                        "\r\n" + //
                        "High-voltage systems require specialized procedures, equipment, training, and safety controls.\r\n"
                        + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 11. Trim and Chassis Assembly\r\n" + //
                        "\r\n" + //
                        "After painting, the vehicle moves toward the trim and chassis areas.\r\n" + //
                        "\r\n" + //
                        "### Typical components installed\r\n" + //
                        "\r\n" + //
                        "* Wiring harnesses\r\n" + //
                        "* Instrument panel\r\n" + //
                        "* Dashboard\r\n" + //
                        "* HVAC components\r\n" + //
                        "* Windshield\r\n" + //
                        "* Door components\r\n" + //
                        "* Headliner\r\n" + //
                        "* Carpets\r\n" + //
                        "* Seats\r\n" + //
                        "* Safety systems\r\n" + //
                        "* Steering components\r\n" + //
                        "* Brake components\r\n" + //
                        "* Suspension components\r\n" + //
                        "\r\n" + //
                        "### Chassis-related activities\r\n" + //
                        "\r\n" + //
                        "* Suspension installation\r\n" + //
                        "* Steering system installation\r\n" + //
                        "* Brake system installation\r\n" + //
                        "* Wheels and tyres\r\n" + //
                        "* Exhaust system for applicable vehicles\r\n" + //
                        "* Fuel system for applicable vehicles\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 12. Final Assembly\r\n" + //
                        "\r\n" + //
                        "The painted body and major vehicle systems are brought together.\r\n" + //
                        "\r\n" + //
                        "This stage is often called the **marriage process**, where the body is joined with the chassis/powertrain assembly.\r\n"
                        + //
                        "\r\n" + //
                        "### Typical sequence\r\n" + //
                        "\r\n" + //
                        "**Painted Body → Interior Installation → Glass → Wiring → Chassis/Powertrain → Wheels/Tyres → Fluids → Battery → Exterior Parts → Software/Electronic Configuration**\r\n"
                        + //
                        "\r\n" + //
                        "### Important controls\r\n" + //
                        "\r\n" + //
                        "* Correct component\r\n" + //
                        "* Correct variant\r\n" + //
                        "* Correct torque\r\n" + //
                        "* Correct part orientation\r\n" + //
                        "* Connector engagement\r\n" + //
                        "* Harness routing\r\n" + //
                        "* Fastener presence\r\n" + //
                        "* Functional operation\r\n" + //
                        "* Traceability\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 13. Torque Control\r\n" + //
                        "\r\n" + //
                        "Torque control is one of the most important manufacturing controls.\r\n" + //
                        "\r\n" + //
                        "Examples include fasteners used for:\r\n" + //
                        "\r\n" + //
                        "* Wheels\r\n" + //
                        "* Suspension\r\n" + //
                        "* Steering\r\n" + //
                        "* Brakes\r\n" + //
                        "* Seats\r\n" + //
                        "* Engine\r\n" + //
                        "* Transmission\r\n" + //
                        "* Battery systems\r\n" + //
                        "* Safety components\r\n" + //
                        "\r\n" + //
                        "### Process controls\r\n" + //
                        "\r\n" + //
                        "1. Define required torque.\r\n" + //
                        "2. Use calibrated tools.\r\n" + //
                        "3. Verify tool settings.\r\n" + //
                        "4. Record torque data where required.\r\n" + //
                        "5. Monitor tool performance.\r\n" + //
                        "6. Stop and investigate abnormal results.\r\n" + //
                        "\r\n" + //
                        "For safety-critical joints, traceability and verification are particularly important.\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 14. Electrical and Software Configuration\r\n" + //
                        "\r\n" + //
                        "Modern vehicles contain many electronic control units.\r\n" + //
                        "\r\n" + //
                        "Examples:\r\n" + //
                        "\r\n" + //
                        "* Engine Control Unit\r\n" + //
                        "* Transmission Control Unit\r\n" + //
                        "* Airbag Control Unit\r\n" + //
                        "* Body Control Module\r\n" + //
                        "* ABS/ESC system\r\n" + //
                        "* Infotainment system\r\n" + //
                        "* ADAS systems\r\n" + //
                        "* Battery Management System\r\n" + //
                        "\r\n" + //
                        "### Manufacturing activities\r\n" + //
                        "\r\n" + //
                        "1. Connect electrical systems.\r\n" + //
                        "2. Check wiring.\r\n" + //
                        "3. Program required software.\r\n" + //
                        "4. Configure vehicle options.\r\n" + //
                        "5. Perform diagnostic scan.\r\n" + //
                        "6. Clear manufacturing faults where appropriate.\r\n" + //
                        "7. Confirm communication between control modules.\r\n" + //
                        "8. Perform functional checks.\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 15. Fluid Filling\r\n" + //
                        "\r\n" + //
                        "Required fluids are filled according to vehicle specifications.\r\n" + //
                        "\r\n" + //
                        "Examples:\r\n" + //
                        "\r\n" + //
                        "* Engine oil\r\n" + //
                        "* Coolant\r\n" + //
                        "* Brake fluid\r\n" + //
                        "* Transmission fluid\r\n" + //
                        "* Windshield washer fluid\r\n" + //
                        "* Refrigerant, where applicable\r\n" + //
                        "\r\n" + //
                        "For EVs, specific cooling and other system requirements apply.\r\n" + //
                        "\r\n" + //
                        "### Important controls\r\n" + //
                        "\r\n" + //
                        "* Correct fluid\r\n" + //
                        "* Correct quantity\r\n" + //
                        "* Cleanliness\r\n" + //
                        "* Filling equipment calibration\r\n" + //
                        "* Leakage verification\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 16. Wheel Alignment and Tyre Installation\r\n" + //
                        "\r\n" + //
                        "The wheels and tyres are installed and checked.\r\n" + //
                        "\r\n" + //
                        "### Checks may include\r\n" + //
                        "\r\n" + //
                        "* Tyre specification\r\n" + //
                        "* Tyre pressure\r\n" + //
                        "* Wheel nut torque\r\n" + //
                        "* Wheel balance\r\n" + //
                        "* Camber\r\n" + //
                        "* Caster\r\n" + //
                        "* Toe\r\n" + //
                        "* Steering center position\r\n" + //
                        "\r\n" + //
                        "Correct wheel alignment is important for vehicle handling, tyre life, and customer satisfaction.\r\n"
                        + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 17. End-of-Line Testing\r\n" + //
                        "\r\n" + //
                        "Every completed vehicle should undergo defined end-of-line checks.\r\n" + //
                        "\r\n" + //
                        "### Typical checks\r\n" + //
                        "\r\n" + //
                        "#### Visual inspection\r\n" + //
                        "\r\n" + //
                        "* Exterior appearance\r\n" + //
                        "* Interior appearance\r\n" + //
                        "* Panel gaps\r\n" + //
                        "* Paint quality\r\n" + //
                        "* Glass\r\n" + //
                        "* Lights\r\n" + //
                        "* Seats\r\n" + //
                        "* Trim\r\n" + //
                        "\r\n" + //
                        "#### Functional checks\r\n" + //
                        "\r\n" + //
                        "* Engine/motor operation\r\n" + //
                        "* Brakes\r\n" + //
                        "* Steering\r\n" + //
                        "* Horn\r\n" + //
                        "* Lights\r\n" + //
                        "* Wipers\r\n" + //
                        "* HVAC\r\n" + //
                        "* Windows\r\n" + //
                        "* Mirrors\r\n" + //
                        "* Infotainment\r\n" + //
                        "* Instrument cluster\r\n" + //
                        "\r\n" + //
                        "#### Electrical checks\r\n" + //
                        "\r\n" + //
                        "* Battery condition\r\n" + //
                        "* Diagnostic scan\r\n" + //
                        "* Warning lights\r\n" + //
                        "* Electronic system communication\r\n" + //
                        "\r\n" + //
                        "#### Leak checks\r\n" + //
                        "\r\n" + //
                        "* Coolant\r\n" + //
                        "* Oil\r\n" + //
                        "* Brake fluid\r\n" + //
                        "* Fuel, where applicable\r\n" + //
                        "* HVAC system\r\n" + //
                        "* Water ingress\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 18. Dynamic Vehicle Testing\r\n" + //
                        "\r\n" + //
                        "Selected vehicles or defined production samples may undergo dynamic testing according to the manufacturer's quality plan.\r\n"
                        + //
                        "\r\n" + //
                        "### Possible tests\r\n" + //
                        "\r\n" + //
                        "* Brake performance\r\n" + //
                        "* Steering behavior\r\n" + //
                        "* Acceleration\r\n" + //
                        "* Gear shifting\r\n" + //
                        "* Noise and vibration\r\n" + //
                        "* Vehicle stability\r\n" + //
                        "* Water leakage\r\n" + //
                        "* Road performance\r\n" + //
                        "* ADAS functionality where applicable\r\n" + //
                        "\r\n" + //
                        "The exact testing frequency and criteria depend on the manufacturer's quality system and regulatory requirements.\r\n"
                        + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 19. Final Quality Inspection\r\n" + //
                        "\r\n" + //
                        "A final quality audit is performed before the vehicle is released.\r\n" + //
                        "\r\n" + //
                        "### Inspection areas\r\n" + //
                        "\r\n" + //
                        "1. Body quality\r\n" + //
                        "2. Paint quality\r\n" + //
                        "3. Interior quality\r\n" + //
                        "4. Electrical functions\r\n" + //
                        "5. Mechanical functions\r\n" + //
                        "6. Safety systems\r\n" + //
                        "7. Vehicle identification\r\n" + //
                        "8. Accessories\r\n" + //
                        "9. Documentation\r\n" + //
                        "10. Customer-specific requirements\r\n" + //
                        "\r\n" + //
                        "Any defect must be recorded and corrected according to the plant's quality procedures.\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 20. Defect Management\r\n" + //
                        "\r\n" + //
                        "Manufacturing defects should be identified, recorded, analyzed, and corrected.\r\n" + //
                        "\r\n" + //
                        "### Typical process\r\n" + //
                        "\r\n" + //
                        "**Defect Detected → Vehicle/Part Identified → Defect Recorded → Containment → Root Cause Analysis → Corrective Action → Verification → Release**\r\n"
                        + //
                        "\r\n" + //
                        "### Common root-cause methods\r\n" + //
                        "\r\n" + //
                        "* 5 Why\r\n" + //
                        "* Fishbone/Ishikawa analysis\r\n" + //
                        "* Pareto analysis\r\n" + //
                        "* Process mapping\r\n" + //
                        "* Failure Mode and Effects Analysis (FMEA)\r\n" + //
                        "* Statistical analysis\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 21. Rework and Repair\r\n" + //
                        "\r\n" + //
                        "If a vehicle does not meet requirements, it is moved to an approved rework or repair process.\r\n"
                        + //
                        "\r\n" + //
                        "### Important principles\r\n" + //
                        "\r\n" + //
                        "* Identify the defect.\r\n" + //
                        "* Define the approved repair method.\r\n" + //
                        "* Use qualified personnel.\r\n" + //
                        "* Record the repair.\r\n" + //
                        "* Reinspect the vehicle.\r\n" + //
                        "* Confirm that the original defect is eliminated.\r\n" + //
                        "* Confirm that no additional defects were introduced.\r\n" + //
                        "\r\n" + //
                        "Vehicles should only be released after meeting the applicable acceptance criteria.\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 22. Traceability\r\n" + //
                        "\r\n" + //
                        "Traceability allows the manufacturer to determine which parts, processes, tools, and production conditions were associated with a vehicle.\r\n"
                        + //
                        "\r\n" + //
                        "### Typical traceability information\r\n" + //
                        "\r\n" + //
                        "* Vehicle Identification Number (VIN)\r\n" + //
                        "* Production date/time\r\n" + //
                        "* Production line\r\n" + //
                        "* Operator/station\r\n" + //
                        "* Component serial number\r\n" + //
                        "* Battery serial number, where applicable\r\n" + //
                        "* Engine number, where applicable\r\n" + //
                        "* Torque records\r\n" + //
                        "* Software version\r\n" + //
                        "* Inspection results\r\n" + //
                        "* Rework history\r\n" + //
                        "\r\n" + //
                        "Traceability is especially important for safety-related components and recall management.\r\n"
                        + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 23. Production Quality Control\r\n" + //
                        "\r\n" + //
                        "Quality should not be checked only at the end of the line.\r\n" + //
                        "\r\n" + //
                        "Controls should be established throughout the manufacturing process.\r\n" + //
                        "\r\n" + //
                        "### Example control points\r\n" + //
                        "\r\n" + //
                        "| Process            | Important Quality Check           |\r\n" + //
                        "| ------------------ | --------------------------------- |\r\n" + //
                        "| Incoming parts     | Part specification and appearance |\r\n" + //
                        "| Stamping           | Dimensions and surface            |\r\n" + //
                        "| Body shop          | Welding and body dimensions       |\r\n" + //
                        "| Paint shop         | Color and coating quality         |\r\n" + //
                        "| Powertrain         | Assembly and functional checks    |\r\n" + //
                        "| Trim               | Component fit and routing         |\r\n" + //
                        "| Final assembly     | Torque and part presence          |\r\n" + //
                        "| Electrical         | Diagnostics and functionality     |\r\n" + //
                        "| Wheel installation | Torque and alignment              |\r\n" + //
                        "| End-of-line        | Vehicle functionality             |\r\n" + //
                        "| Final audit        | Overall vehicle quality           |\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 24. Production Planning and Line Control\r\n" + //
                        "\r\n" + //
                        "Manufacturing requires continuous coordination between production, logistics, quality, maintenance, engineering, and suppliers.\r\n"
                        + //
                        "\r\n" + //
                        "### Important activities\r\n" + //
                        "\r\n" + //
                        "1. Production scheduling.\r\n" + //
                        "2. Material planning.\r\n" + //
                        "3. Line balancing.\r\n" + //
                        "4. Operator allocation.\r\n" + //
                        "5. Parts replenishment.\r\n" + //
                        "6. Equipment monitoring.\r\n" + //
                        "7. Production tracking.\r\n" + //
                        "8. Downtime monitoring.\r\n" + //
                        "9. Quality monitoring.\r\n" + //
                        "10. Daily production review.\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 25. Maintenance\r\n" + //
                        "\r\n" + //
                        "Manufacturing equipment must be maintained to prevent unexpected failures.\r\n" + //
                        "\r\n" + //
                        "### Types of maintenance\r\n" + //
                        "\r\n" + //
                        "**Preventive Maintenance**\r\n" + //
                        "\r\n" + //
                        "Scheduled maintenance performed before equipment failure.\r\n" + //
                        "\r\n" + //
                        "**Predictive Maintenance**\r\n" + //
                        "\r\n" + //
                        "Uses equipment condition/data to predict potential failures.\r\n" + //
                        "\r\n" + //
                        "**Corrective Maintenance**\r\n" + //
                        "\r\n" + //
                        "Repair performed after a failure or abnormal condition is identified.\r\n" + //
                        "\r\n" + //
                        "### Equipment examples\r\n" + //
                        "\r\n" + //
                        "* Stamping presses\r\n" + //
                        "* Welding robots\r\n" + //
                        "* Conveyors\r\n" + //
                        "* Paint equipment\r\n" + //
                        "* Torque tools\r\n" + //
                        "* Lifting equipment\r\n" + //
                        "* Test equipment\r\n" + //
                        "* Automated inspection systems\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 26. Safety Management\r\n" + //
                        "\r\n" + //
                        "Safety is a critical part of automotive manufacturing.\r\n" + //
                        "\r\n" + //
                        "### Important areas\r\n" + //
                        "\r\n" + //
                        "* Machine guarding\r\n" + //
                        "* Lockout/tagout\r\n" + //
                        "* PPE\r\n" + //
                        "* Welding safety\r\n" + //
                        "* Chemical handling\r\n" + //
                        "* Fire protection\r\n" + //
                        "* Ergonomics\r\n" + //
                        "* Material handling\r\n" + //
                        "* Electrical safety\r\n" + //
                        "* High-voltage EV safety\r\n" + //
                        "* Emergency response\r\n" + //
                        "\r\n" + //
                        "Employees should be trained and authorized for the tasks they perform.\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 27. Logistics and Material Flow\r\n" + //
                        "\r\n" + //
                        "Parts must reach the correct workstation at the correct time.\r\n" + //
                        "\r\n" + //
                        "A typical flow is:\r\n" + //
                        "\r\n" + //
                        "**Supplier → Receiving → Warehouse → Line-Side Storage → Production Station → Vehicle**\r\n" + //
                        "\r\n" + //
                        "Modern plants may use:\r\n" + //
                        "\r\n" + //
                        "* Just-in-Time delivery\r\n" + //
                        "* Just-in-Sequence delivery\r\n" + //
                        "* Kanban\r\n" + //
                        "* Automated Guided Vehicles\r\n" + //
                        "* Warehouse Management Systems\r\n" + //
                        "* Barcode/RFID tracking\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 28. Continuous Improvement\r\n" + //
                        "\r\n" + //
                        "Automotive plants continuously improve their processes.\r\n" + //
                        "\r\n" + //
                        "Common objectives include:\r\n" + //
                        "\r\n" + //
                        "* Reduce defects\r\n" + //
                        "* Reduce production time\r\n" + //
                        "* Reduce waste\r\n" + //
                        "* Reduce equipment downtime\r\n" + //
                        "* Improve worker safety\r\n" + //
                        "* Improve productivity\r\n" + //
                        "* Reduce manufacturing cost\r\n" + //
                        "* Improve customer quality\r\n" + //
                        "\r\n" + //
                        "Common improvement approaches include:\r\n" + //
                        "\r\n" + //
                        "* Lean Manufacturing\r\n" + //
                        "* Kaizen\r\n" + //
                        "* Six Sigma\r\n" + //
                        "* Standardized Work\r\n" + //
                        "* 5S\r\n" + //
                        "* Value Stream Mapping\r\n" + //
                        "* Poka-Yoke\r\n" + //
                        "* OEE improvement\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 29. Final Vehicle Release\r\n" + //
                        "\r\n" + //
                        "Before shipment, the vehicle must meet all applicable release requirements.\r\n" + //
                        "\r\n" + //
                        "### Final release activities\r\n" + //
                        "\r\n" + //
                        "1. Complete quality checks.\r\n" + //
                        "2. Confirm required repairs are closed.\r\n" + //
                        "3. Verify vehicle identification.\r\n" + //
                        "4. Verify software/configuration.\r\n" + //
                        "5. Confirm documentation.\r\n" + //
                        "6. Confirm accessories.\r\n" + //
                        "7. Confirm cleanliness.\r\n" + //
                        "8. Perform final inspection.\r\n" + //
                        "9. Release vehicle in the production system.\r\n" + //
                        "10. Move vehicle to finished-vehicle storage or dispatch.\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 30. Vehicle Dispatch\r\n" + //
                        "\r\n" + //
                        "The finished vehicle is transported to the dealer, distributor, customer, or another designated location.\r\n"
                        + //
                        "\r\n" + //
                        "### Dispatch process\r\n" + //
                        "\r\n" + //
                        "**Final Release → Yard Entry → Vehicle Identification → Shipping Documentation → Vehicle Loading → Transportation → Dealer/Customer**\r\n"
                        + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 31. Key Manufacturing Documents\r\n" + //
                        "\r\n" + //
                        "A car manufacturing organization typically maintains many controlled documents.\r\n" + //
                        "\r\n" + //
                        "Important examples include:\r\n" + //
                        "\r\n" + //
                        "* Process Flow Diagram\r\n" + //
                        "* Standard Operating Procedure (SOP)\r\n" + //
                        "* Standardized Work Instructions\r\n" + //
                        "* Control Plan\r\n" + //
                        "* PFMEA\r\n" + //
                        "* Work Instructions\r\n" + //
                        "* Engineering Drawings\r\n" + //
                        "* Inspection Standards\r\n" + //
                        "* Quality Check Sheets\r\n" + //
                        "* Torque Specifications\r\n" + //
                        "* Maintenance Standards\r\n" + //
                        "* Calibration Records\r\n" + //
                        "* Supplier Quality Documents\r\n" + //
                        "* Material Specifications\r\n" + //
                        "* Traceability Records\r\n" + //
                        "* Non-Conformance Reports\r\n" + //
                        "* Corrective Action Reports\r\n" + //
                        "* Final Inspection Records\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 32. Simplified End-to-End Process\r\n" + //
                        "\r\n" + //
                        "The complete process can be summarized as:\r\n" + //
                        "\r\n" + //
                        "**1. Product Planning**\r\n" + //
                        "↓\r\n" + //
                        "**2. Vehicle Design & Engineering**\r\n" + //
                        "↓\r\n" + //
                        "**3. Supplier Development**\r\n" + //
                        "↓\r\n" + //
                        "**4. Parts Procurement**\r\n" + //
                        "↓\r\n" + //
                        "**5. Incoming Inspection**\r\n" + //
                        "↓\r\n" + //
                        "**6. Sheet Metal Stamping**\r\n" + //
                        "↓\r\n" + //
                        "**7. Body Shop / BIW Construction**\r\n" + //
                        "↓\r\n" + //
                        "**8. Paint Shop**\r\n" + //
                        "↓\r\n" + //
                        "**9. Engine / Motor / Powertrain Assembly**\r\n" + //
                        "↓\r\n" + //
                        "**10. Trim & Chassis Assembly**\r\n" + //
                        "↓\r\n" + //
                        "**11. Final Assembly**\r\n" + //
                        "↓\r\n" + //
                        "**12. Electrical & Software Configuration**\r\n" + //
                        "↓\r\n" + //
                        "**13. Fluid Filling**\r\n" + //
                        "↓\r\n" + //
                        "**14. Wheel Alignment & Tyre Installation**\r\n" + //
                        "↓\r\n" + //
                        "**15. End-of-Line Testing**\r\n" + //
                        "↓\r\n" + //
                        "**16. Final Quality Audit**\r\n" + //
                        "↓\r\n" + //
                        "**17. Rework if Required**\r\n" + //
                        "↓\r\n" + //
                        "**18. Final Vehicle Release**\r\n" + //
                        "↓\r\n" + //
                        "**19. Dispatch to Dealer/Customer**\r\n" + //
                        "\r\n" + //
                        "---\r\n" + //
                        "\r\n" + //
                        "# 33. Key Success Factors\r\n" + //
                        "\r\n" + //
                        "A successful car manufacturing process depends on:\r\n" + //
                        "\r\n" + //
                        "1. **Safety** – Employees and customers must be protected.\r\n" + //
                        "2. **Quality** – Vehicles must meet engineering and customer requirements.\r\n" + //
                        "3. **Cost** – Manufacturing must remain commercially viable.\r\n" + //
                        "4. **Delivery** – Vehicles must be produced according to schedule.\r\n" + //
                        "5. **Productivity** – Resources and equipment should be used efficiently.\r\n" + //
                        "6. **Traceability** – Important production information must be recorded.\r\n" + //
                        "7. **Standardization** – Processes must be performed consistently.\r\n" + //
                        "8. **Continuous Improvement** – Problems should be systematically eliminated.\r\n" + //
                        "9. **Supplier Quality** – Purchased components must meet requirements.\r\n" + //
                        "10. **Regulatory Compliance** – Applicable legal and technical requirements must be met.\r\n" + //
                        "\r\n" + //
                        "## Conclusion\r\n" + //
                        "\r\n" + //
                        "Car manufacturing is a highly integrated process involving **engineering, supply chain, stamping, body construction, painting, powertrain production, assembly, electronics, quality control, testing, logistics, and continuous improvement**.\r\n"
                        + //
                        "\r\n" + //
                        "The most important principle is that quality must be built into every manufacturing stage rather than relying only on final inspection. A well-controlled manufacturing system uses standardized processes, trained personnel, controlled equipment, traceability, quality checks, and systematic problem solving to consistently produce safe and reliable vehicles.\r\n"
                        + //
                        "");

        assertNotNull(res);
        assertEquals("Meeting Notes", res.category());
        assertEquals(85, res.confidence());
    }

    @Test
    void classify_withJsonStringFallback_parsesAndReturns() throws Exception {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(chatClient);

        // Model returned a JSON string instead of a map; ensure fallback parsing works
        String json = "{\"category\":\"Policy Document\",\"confidence\":90}";

        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(eq(Map.class)))
                .thenAnswer(invocation -> (Object) json);

        AiClassificationService svc = new AiClassificationService(builder);
        ClassificationResultDTO res = svc.classifyDocument("Policy content here");

        assertNotNull(res);
        assertEquals("Policy Document", res.category());
        assertEquals(90, res.confidence());
    }

    @Test
    void classify_malformedResponse_returnsUnknown() throws Exception {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(builder.build()).thenReturn(chatClient);

        // Missing confidence field
        when(chatClient.prompt().system(anyString()).user(anyString()).call().entity(eq(Map.class)))
                .thenReturn(Map.of("category", "Requirements Document"));

        AiClassificationService svc = new AiClassificationService(builder);
        ClassificationResultDTO res = svc.classifyDocument("Some requirement text");

        assertNotNull(res);
        assertEquals("Unknown", res.category());
        assertEquals(0, res.confidence());
    }
}
