package app.listful.sharing;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PublicSharePageController {
    @GetMapping("/s/{token}")
    public String publicSharePage() {
        return "forward:/index.html";
    }
}
