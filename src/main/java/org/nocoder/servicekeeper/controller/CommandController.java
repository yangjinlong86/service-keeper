package org.nocoder.servicekeeper.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/command")
public class CommandController {

    @GetMapping("")
    public String command() {
        return "command";
    }

}